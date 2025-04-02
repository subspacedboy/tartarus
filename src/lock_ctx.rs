use std::fmt::Error;
use std::io::Read;
use std::time::Duration;
use base64::Engine;
use base64::engine::general_purpose;
use base64::prelude::{BASE64_STANDARD, BASE64_URL_SAFE};
use data_encoding::{BASE32_NOPAD, BASE64, BASE64_NOPAD};
use display_interface_spi::SPIInterface;
use embedded_graphics::mono_font::ascii::FONT_6X10;
use embedded_graphics::mono_font::MonoTextStyle;
use embedded_graphics::prelude::Primitive;
use embedded_graphics::primitives::PrimitiveStyleBuilder;
use embedded_graphics::text::{Text, TextStyle};
use embedded_graphics_core::Drawable;
use embedded_graphics_core::pixelcolor::Rgb565;
use embedded_graphics_core::prelude::{DrawTarget, Point, RgbColor, Size};
use embedded_graphics_core::primitives::Rectangle;
use esp_idf_hal::gpio::{Gpio40, Gpio41, Gpio45, GpioError, Output, PinDriver};
use esp_idf_hal::spi::{SpiDeviceDriver, SpiDriver};
use esp_idf_svc::http::client::EspHttpConnection;
use esp_idf_svc::nvs::{EspNvs, NvsDefault};
use esp_idf_svc::wifi::{BlockingWifi, EspWifi};
use p256::{PublicKey, SecretKey};
use p256::pkcs8::{EncodePublicKey, LineEnding};
use rand_core::RngCore;
use st7789::ST7789;
use crate::boot_screen::BootScreen;
use crate::contract_generated::club::subjugated::fb::message::{Contract, LockUpdateEvent, LockUpdateEventArgs, MessagePayload, SignedMessage, SignedMessageArgs, UpdateType};
use crate::Esp32Rng;
use crate::internal_contract::InternalContract;
use crate::overlays::{ButtonPressOverlay, WifiOverlay};
use crate::prelude::prelude;
use crate::prelude::prelude::{DynOverlay, DynScreen, MyDisplay, MySPI};
use crate::screen_state::ScreenState;
use crate::wifi_util::{connect_wifi, parse_wifi_qr};
use embedded_svc::http::client::Client as HttpClient;
use esp_idf_hal::io::Write;
use flatbuffers::FlatBufferBuilder;
use p256::ecdsa::signature::Signer;
use p256::ecdsa::{Signature, SigningKey};
use sha2::{Digest, Sha256};

#[derive(Debug, Clone)]
pub struct TickUpdate {
    pub d0_pressed : bool,
    pub d1_pressed : bool,
    pub d2_pressed : bool,
    pub qr_data : Option<Vec<u8>>,
    pub since_last: Duration
}

pub struct LockCtx {
    pub(crate) display: MyDisplay<'static>,
    nvs: EspNvs<NvsDefault>,
    current_screen: Option<Box<DynScreen<'static>>>,
    overlays: Vec<Box<DynOverlay<'static>>>,
    wifi: BlockingWifi<EspWifi<'static>>,
    pub(crate) wifi_connected: bool,
    pub(crate) secret_key: Option<SecretKey>,
    pub(crate) public_key: Option<PublicKey>,
    pub(crate) this_update: Option<TickUpdate>,
    pub(crate) contract: Option<InternalContract>,
    is_locked: bool,
    pub(crate) session_token: String,
}

impl LockCtx {
    pub fn new(display: MyDisplay<'static>, nvs: EspNvs<NvsDefault>, wifi: BlockingWifi<EspWifi<'static>>) -> Self {
        let mut lck = LockCtx {
            display,
            nvs,
            wifi,
            wifi_connected: false,
            overlays: Vec::new(),
            current_screen: None,
            secret_key: None,
            public_key: None,
            this_update: None,
            contract: None,
            is_locked: false,
            session_token: String::new(),
        };

        lck.session_token = lck.load_or_create_session_id();
        let secret_key = lck.load_or_create_key();
        let my_public = PublicKey::from_secret_scalar(&secret_key.to_nonzero_scalar());

        lck.secret_key = Some(secret_key);
        lck.public_key = Some(my_public);

        // This must come after key loading because we're announcing to the coordinator with our
        // public key.
        if let Ok(connected) = lck.wifi.is_connected() {
            lck.wifi_connected = connected;

            lck.start_announce_to_coordinator();
        }

        let wifi_overlay: Box<DynOverlay<'static>> = Box::new(
            WifiOverlay::<
                MySPI<'static>,
                PinDriver<'static, _, Output>,
                PinDriver<'static, _, Output>,
                GpioError
            >::new()
        );
        lck.overlays.push(wifi_overlay);

        let button_overlay: Box<DynOverlay<'static>> = Box::new(
            ButtonPressOverlay::<
                MySPI<'static>,
                PinDriver<'static, _, Output>,
                PinDriver<'static, _, Output>,
                GpioError
            >::new()
        );
        lck.overlays.push(button_overlay);

        let boot_screen: Box<DynScreen<'static>> = Box::new(
            BootScreen::<
                MySPI<'static>,
                PinDriver<'static, _, Output>,
                PinDriver<'static, _, Output>,
                GpioError
            >::new()
        );
        lck.current_screen = Some(boot_screen);

        lck.display.clear(Rgb565::WHITE).unwrap();

        lck
    }

    fn load_or_create_session_id(&mut self) -> String {
        let key_name = "session_key";
        let mut buffer : [u8; 32] = [0; 32];
        let existing_key: Option<&[u8]> = self.nvs.get_blob(key_name, &mut buffer).ok().expect("The NVS mechanism should have worked");
        let mut rng = Esp32Rng;

        let session_id : String = if let Some(token_bytes) = existing_key {
            let token = String::from_utf8(token_bytes.to_vec()).unwrap();
            log::info!("Loaded existing session ID: {:?}", token);
            token
        } else {
            log::info!("Generating a new session ID...");
            let mut bytes = [0u8; 5]; // 5 bytes -> ~6 base32 chars
            rng.fill_bytes(&mut bytes);
            let token = BASE32_NOPAD.encode(&bytes)[..6].to_string();
            self.nvs.set_blob(key_name, token.as_bytes()).unwrap();
            token
        };

        session_id
    }

    fn load_or_create_key(&mut self) -> SecretKey {
        let key_name = "ec_private";
        let mut buffer : [u8; 256] = [0; 256];
        let existing_key: Option<&[u8]> = self.nvs.get_blob(key_name, &mut buffer).ok().expect("The NVS mechanism should have worked");
        let mut rng = Esp32Rng;

        let private_key : SecretKey = if let Some(base64_key) = existing_key {
            log::info!("Loaded existing EC private key.");
            let key_bytes = general_purpose::STANDARD.decode(base64_key).unwrap();
            SecretKey::from_slice(&key_bytes).expect("Failed to parse private key")
        } else {
            log::info!("Generating a new EC private key...");
            let sk = SecretKey::random(&mut rng);

            let key_bytes = sk.to_bytes();
            let encoded_key = general_purpose::STANDARD.encode(key_bytes);
            self.nvs.set_blob(key_name, encoded_key.as_bytes()).unwrap();
            log::info!("Saved new EC private key to NVS.");
            sk
        };

        private_key
    }

    pub fn tick(&mut self, update : TickUpdate) -> () {
        self.this_update = Some(update.clone());

        if let Some(mut current_screen) = self.current_screen.take() {
            let result_from_update = current_screen.on_update(self);

            if let Some(mut new_screen) = result_from_update {
                log::info!("New screen -> {:?}", new_screen.get_id());
                // We state transitioned. So mandatory clear + draw.
                self.display.clear(Rgb565::WHITE).unwrap();
                new_screen.draw_screen(self);
                self.current_screen = Some(new_screen);
            } else {
                if current_screen.needs_redraw() {
                    current_screen.draw_screen(self);
                }
                // Put the current screen back
                self.current_screen = Some(current_screen);
            }

        }

        let mut overlays = core::mem::take(&mut self.overlays);
        for overlay in overlays.iter_mut() {
            overlay.draw_screen(self);
        }
        self.overlays = overlays;

        if let Some(incoming_data) = update.qr_data.clone() {
            if let Ok(maybe_string) = String::from_utf8(incoming_data) {
                if maybe_string.starts_with("WIFI:") {
                    let maybe_creds = parse_wifi_qr(maybe_string);
                    if let Some((ssid, password)) = maybe_creds {
                        log::info!("Trying to connect to wifi -> SSID[{}] Password[{}]", ssid, password);

                        let password = "H0la Chic0s";

                        let mut tries = 3;
                        let mut connected = false;
                        while tries > 0 && !connected {
                            if let Ok(_) = connect_wifi(&mut self.wifi, &ssid, &String::from(password)) {
                                log::info!("Connected!");
                                self.wifi_connected = true;
                                connected = true;

                                self.nvs.set_blob("ssid", ssid.as_bytes()).unwrap();
                                self.nvs.set_blob("password", password.as_bytes()).unwrap();
                            } else {
                                log::info!("Failed to connect");
                                tries -=1;
                            }
                        }

                        if !connected {
                            log::info!("Failed to connect and ran out of tries :-(");
                        }

                    }
                }
            }
        }//end of if let Some(incoming_data)

    }

    pub fn cycle_lock(&mut self) -> () {
        log::info!("Cycling lock");
        self.is_locked ^= self.is_locked;
    }

    pub fn lock(&mut self) -> () {
        log::info!("Locking");
        self.is_locked = true;
    }

    pub fn unlock(&mut self) -> () {
        log::info!("Unlocking");
        self.is_locked = false;
    }

    pub fn is_locked(&self) -> bool {
        self.is_locked
    }

    pub fn get_lock_url(&self) -> Result<String, String> {
        const COORDINATOR: &str = "http://192.168.1.180:4200/lock-start";
        if let Some(pub_key) = self.public_key {
            let compressed_bytes = pub_key.to_sec1_bytes();
            let encoded_key = BASE64_URL_SAFE.encode(&compressed_bytes);
            // let encoded_pub_key = general_purpose::STANDARD.encode(pub_key.to_public_key_pem(LineEnding::CR).unwrap());
            Ok(format!("{}?public={}&session={}", COORDINATOR, encoded_key, self.session_token))
        } else {
            Err("Wasn't able to make the url?!".parse().unwrap())
        }

    }

    pub fn accept_contract(&mut self, contract : &InternalContract) -> () {
    }

    fn start_announce_to_coordinator(&self) {
        const URL: &str = "http://192.168.1.180:5002/event";
        let mut pos = EspHttpConnection::new(&mut Default::default()).expect("HTTP client should be available");
        let mut client = HttpClient::wrap(&mut pos);

        let mut builder = FlatBufferBuilder::with_capacity(1024);
        let public_key: Vec<u8> = self.public_key.unwrap().to_sec1_bytes().as_ref().clone().to_vec();
        let session = builder.create_string(&self.session_token);
        let this_update_type = UpdateType::Started;
        let body = builder.create_string("lock update body");

        let pub_key_holder = builder.create_vector(&public_key);
        let lock_update_event = LockUpdateEvent::create(
            &mut builder,
            &LockUpdateEventArgs {
                public_key: Some(pub_key_holder),
                session: Some(session),
                body: Some(body),
                this_update_type,
            },
        );

        let payload_type = MessagePayload::LockUpdateEvent; // Union type
        let payload_value = lock_update_event.as_union_value();

        builder.finish(lock_update_event, None);
        let buffer = builder.finished_data();

        let table_offset = buffer[0] as usize;
        let vtable_offset = buffer[table_offset] as usize;
        let actual_start = table_offset - vtable_offset;

        log::info!("Update buffer w/ vtable ({},{}): {:?}", vtable_offset, table_offset, buffer);
        let hash = Sha256::digest(&buffer[actual_start..]);
        log::info!("Hash {:?}", hash);

        // UGH. We have to build the whole message over again because of the way
        // Rust implements flatbuffers.
        let mut builder = FlatBufferBuilder::with_capacity(1024);
        let public_key: Vec<u8> = self.public_key.unwrap().to_sec1_bytes().as_ref().clone().to_vec();
        let session = builder.create_string(&self.session_token);
        let this_update_type = UpdateType::Started;
        let body = builder.create_string("lock update body");

        let pub_key_holder = builder.create_vector(&public_key);
        let lock_update_event = LockUpdateEvent::create(
            &mut builder,
            &LockUpdateEventArgs {
                public_key: Some(pub_key_holder),
                session: Some(session),
                body: Some(body),
                this_update_type,
            },
        );

        let payload_type = MessagePayload::LockUpdateEvent; // Union type
        let payload_value = lock_update_event.as_union_value();

        let secret = self.secret_key.as_ref().unwrap();
        let pem = secret.to_sec1_pem(Default::default()).unwrap();

        let cloned_secret = secret.clone();
        let bytes = cloned_secret.to_bytes();
        let key_bytes = bytes.as_slice();
        let signing_key = SigningKey::from_slice(key_bytes).unwrap();
        let signature: Signature = signing_key.sign(&hash.as_slice());
        // let signing_key = SigningKey::from_bytes(*secret.as_scalar_primitive());
        // let signing_key = /

        // let signature: Vec<u8> = vec![9, 8, 7, 6, 5];
        let sig_bytes = signature.to_bytes();

        let signature_offset = builder.create_vector(sig_bytes.as_slice());
        let signed_message = SignedMessage::create(
            &mut builder,
            &SignedMessageArgs {
                signature: Some(signature_offset),
                payload: Some(payload_value),
                payload_type,
            },
        );

        builder.finish(signed_message, None);
        let data = builder.finished_data().to_vec();

        if let Ok(mut request) = client.post(&URL, &[("Content-Type","application/octet-stream")]) {
            if let Ok(result) =request.write_all(data.as_slice()) {
                if let Ok(_) = request.flush() {}
            }

            if let Ok(response) = request.submit() {
                log::info!("Response status: {}", response.status());
            } else {
                log::error!("Failed to send request");
            }
        } else {
            log::error!("Failed to POST request");
        }
    }
}