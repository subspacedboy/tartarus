use std::io::Read;
use sha2::Digest;
mod contract_generated;

use contract_generated::*;

use std::thread::sleep;
use std::time::Duration;

use aes_gcm::{Aes256Gcm, Key, KeyInit};
use aes_gcm::aead::{Aead, Nonce, Payload};
use aes_gcm::aead::generic_array::GenericArray;
use base64::Engine;
use base64::engine::general_purpose;
use display_interface_spi::SPIInterfaceNoCS;
use embedded_graphics::Drawable;
use embedded_graphics::geometry::Point;
use embedded_graphics::pixelcolor::Rgb565;
use embedded_graphics::prelude::*;
use embedded_graphics::primitives::Primitive;
use embedded_graphics::primitives::*;
use embedded_hal::spi::MODE_3;
use embedded_hal_compat::eh0_2::digital::v1_compat::OldOutputPin;
use esp_idf_hal::delay::BLOCK;
use esp_idf_hal::gpio::{AnyIOPin, PinDriver};
use esp_idf_hal::i2c::{I2cConfig, I2cDriver};
use esp_idf_hal::peripherals::Peripherals;
use esp_idf_hal::prelude::FromValueType;
use esp_idf_hal::spi;
use esp_idf_hal::spi::{SpiConfig, SpiDeviceDriver, SpiDriver};
use esp_idf_hal::units::KiloHertz;
use esp_idf_svc::eventloop::EspSystemEventLoop;
use esp_idf_svc::hal::delay;
use esp_idf_svc::hal::gpio;
use esp_idf_svc::sys::esp_random;
use esp_idf_svc::nvs::{EspDefaultNvsPartition, EspNvs};
use esp_idf_svc::wifi::{AuthMethod, BlockingWifi, ClientConfiguration, Configuration, EspWifi};
use esp_idf_svc::http::client::EspHttpConnection;

use embedded_svc::http::client::Client as HttpClient;
use embedded_svc::io::Write;

use hkdf::Hkdf;
use p256::{PublicKey, SecretKey};
use p256::ecdh::diffie_hellman;
use p256::ecdsa::{Signature, VerifyingKey};
use p256::pkcs8::{DecodePublicKey, EncodePublicKey, LineEnding};
use p256::ecdsa::signature::Verifier;
use qrcode::{Color, QrCode};
use rand_core::{CryptoRng, RngCore};
use sha2::{digest, Sha256};
use st7789::{Orientation, ST7789};
use crate::contract_generated::subjugated::club::{MessagePayload, SignedMessage};

struct Esp32Rng;

impl RngCore for Esp32Rng {
    fn next_u32(&mut self) -> u32 {
        unsafe {
            return esp_random() as u32
        }
    }

    fn next_u64(&mut self) -> u64 {
        let mut bytes = [0u8; 8];
        self.fill_bytes(&mut bytes);
        u64::from_le_bytes(bytes)
    }

    fn fill_bytes(&mut self, dest: &mut [u8]) {
        for chunk in dest.chunks_mut(4) {
            let rand = self.next_u32();
            for (i, byte) in chunk.iter_mut().enumerate() {
                *byte = (rand >> (i * 8)) as u8;
            }
        }
    }

    fn try_fill_bytes(&mut self, dest: &mut [u8]) -> Result<(), rand_core::Error> {
        self.fill_bytes(dest);
        Ok(())
    }
}

impl CryptoRng for Esp32Rng {}

//noinspection RsUnresolvedMethod
fn main() {
    // It is necessary to call this function once. Otherwise some patches to the runtime
    // implemented by esp-idf-sys might not link properly. See https://github.com/esp-rs/esp-idf-template/issues/71
    esp_idf_svc::sys::link_patches();

    // Bind the log crate to the ESP Logging facilities
    esp_idf_svc::log::EspLogger::initialize_default();

    log::info!("SmartLock V2.0");

    let peripherals = Peripherals::take().expect("Peripherals to be available");

    let mut config = SpiConfig::default();
    config.baudrate = 10_000_000.Hz();
    config.data_mode = MODE_3;

    // let mut config1 = DriverConfig::default();

    let mut tft_power = PinDriver::output(peripherals.pins.gpio21).expect("pin to be available");
    tft_power.set_high().expect("tft power to be high");

    let sclk = peripherals.pins.gpio36;
    let sdo = peripherals.pins.gpio35;// Mosi
    let sdi = peripherals.pins.gpio37;
    // let rst = PinDriver::output(/**/).expect("Pin to be available");
    // let rst = peripherals.pins.gpio40.downgrade_output();
    let mut rst = PinDriver::output(peripherals.pins.gpio40).expect("to work");
    rst.set_low().expect("Reset to be low");
    // let mut old_rst = OldOutputPin::new(rst);
    let old_rst = OldOutputPin::new(rst);
    let mut bl = PinDriver::output(peripherals.pins.gpio45).expect("to work");
    bl.set_high().expect("BL to be high");
    let old_bl = OldOutputPin::new(bl);
    let dc = peripherals.pins.gpio39;
    let cs = peripherals.pins.gpio7;

    let spi: SpiDeviceDriver<SpiDriver> = spi::SpiDeviceDriver::new_single(
        peripherals.spi2,
        sclk,
        sdo,
        Some(sdi),
        Some(cs),
        &spi::SpiDriverConfig::new().dma(spi::Dma::Disabled),
        &spi::SpiConfig::new().baudrate(10.MHz().into()),
    ).expect("SpiDeviceDriver to work");

    let di = SPIInterfaceNoCS::new(
        spi,
        gpio::PinDriver::output(dc).expect("Pin driver for DC to work"),
    );

    // let wrapper = SPIInterface::new(spi, dc, cs);
    let mut display = ST7789::new(di, Some(old_rst), Some(old_bl), 240, 135);

    display.init(&mut delay::Ets).expect("Display to initialize");
    display.set_orientation(Orientation::Portrait).expect("To set landscape");

    display.clear(Rgb565::WHITE).expect("Display to clear");

    let nvs_partition = EspDefaultNvsPartition::take().expect("EspDefaultNvsPartition to be available");
    let mut nvs = EspNvs::new(nvs_partition.clone(), "storage", true).unwrap();

    let key_name = "ec_private";
    let mut buffer : [u8; 256] = [0; 256];
    let existing_key: Option<&[u8]> = nvs.get_blob(key_name, &mut buffer).ok().expect("The NVS mechanism should have worked");
    let mut rng = Esp32Rng;

    let private_key : SecretKey = if let Some(base64_key) = existing_key {
        log::info!("Loaded existing EC private key.");
        let key_bytes = general_purpose::STANDARD.decode(base64_key).unwrap();
        // SecretKey::from_sec1_der()
        SecretKey::from_slice(&key_bytes).expect("Failed to parse private key")
    } else {
        log::info!("Generating a new EC private key...");
        let sk = SecretKey::random(&mut rng);

        // // Serialize & encode in base64
        let key_bytes = sk.to_bytes();
        let encoded_key = general_purpose::STANDARD.encode(key_bytes);
        nvs.set_blob(key_name, encoded_key.as_bytes()).unwrap();
        log::info!("Saved new EC private key to NVS.");
        sk
    };

    let my_public = PublicKey::from_secret_scalar(&private_key.to_nonzero_scalar());

    let encoded_cert = general_purpose::STANDARD.encode(&my_public.to_public_key_pem(LineEnding::CR).unwrap());

    const COORDINATOR: &str = "http://192.168.1.168:5002";
    let whole_message = format!("{}/?public={}", COORDINATOR, encoded_cert);

    log::info!("Message: {}", whole_message);

    let qr = QrCode::new(whole_message).expect("Valid QR code");
    let qr_width = qr.width() as u32;

    log::info!("Generated code with version: {:?}", qr.version());
    log::info!("Has width: {:?}", qr_width);

    // Scale factor and positioning
    let scale = 2;
    let offset_x = (240 - qr_width * scale) / 2;
    let offset_y = (240 - qr_width * scale) / 2;

    for y in 0..qr_width {
        for x in 0..qr_width {
            let color = if qr[(x as usize, y as usize)] == Color::Dark {
                Rgb565::BLACK
            } else {
                Rgb565::WHITE
            };
            let rect = Rectangle::new(
                Point::new((offset_x + x * scale) as i32, (offset_y + y * scale) as i32),
                Size::new(scale, scale),
            )
                .into_styled(PrimitiveStyleBuilder::new().fill_color(color).build());
            rect.draw(&mut display).expect("Expected to draw");
        }
    }

    log::info!("Putting wifi on standby");

    let sys_loop = EspSystemEventLoop::take().expect("EspSystemEventLoop to be available");
    // let timer_service = EspTaskTimerService::new().expect("Timer to be available");

    let mut wifi_connected = false;

    let mut wifi = BlockingWifi::wrap(
        EspWifi::new(peripherals.modem, sys_loop.clone(), Some(nvs_partition)).expect("EspWifi to work"),
        sys_loop,
    ).expect("Wifi to start");
    wifi.start().expect("Wifi should have started");

    // See if we have pre-existing creds
    let mut buffer : [u8; 256] = [0; 256];
    if let Ok(blob) = nvs.get_blob("ssid", &mut buffer) {
        if let Some(actual_ssid) = blob {
            // Ok, we have an SSID. Let's assume we have a matching password.
            let mut pass_buffer : [u8; 256] = [0; 256];
            let pass = nvs.get_blob("password", &mut pass_buffer).expect("NVS to work");
            if let Some(actual_pass) = pass {
                let ssid = String::from_utf8(actual_ssid.to_vec()).ok().unwrap();
                let password = String::from_utf8(actual_pass.to_vec()).ok().unwrap();
                if let Ok(_) = connect_wifi(&mut wifi, &ssid, &password) {
                    log::info!("Was able to join wifi from stored credentials :-)");
                    wifi_connected = true;
                } else {
                    log::info!("Was NOT able to join wifi :-( ");
                }
            }
        }
    } else {
        log::info!("No stored wifi credentials. Deferring wifi");
    }

    if wifi_connected {
        let url = format!("{}/announce?public={}", COORDINATOR, encoded_cert);
        let mut pos = EspHttpConnection::new(&mut Default::default()).expect("HTTP client should be available");
        let mut client = HttpClient::wrap(&mut pos);

        let mut request = client.post(&url, &[]).unwrap();
        let payload = b"{\"key\": \"value\"}";
        request.write_all(payload).expect("All to write");
        request.flush().expect("Flush to succeed");

        let response = request.submit().expect("Failed to send");
        log::info!("Response status: {}", response.status());
    }

    log::info!("Initializing code reader");
    const READER_ADDRESS: u8 = 0x0c;
    let sda: AnyIOPin = peripherals.pins.gpio42.into();
    let scl: AnyIOPin = peripherals.pins.gpio41.into();

    let config = I2cConfig::new().baudrate(KiloHertz(400).into());
    let mut i2c_driver = I2cDriver::new(
        peripherals.i2c0,
        sda,
        scl,
        &config,
    ).expect("I2C to initialize");

    // The maximum dataframe size from the tiny code reader is
    // 254 (size of rx_buf). The first byte in the message is the length
    // of the read message with an offset of 2. One for size, and one for
    // the null byte after size.
    let mut rx_buf: [u8; 254] = [0; 254];

    // Draw the circle on the display

    log::info!("Ready :-)");
    let mut sm = StateMachine::new();
    let mut public_key : Option<PublicKey> = None;
    let mut cipher : Option<Aes256Gcm> = None;

    loop {
        let mut data : Option<Vec<u8>> = None;
        match i2c_driver.read(READER_ADDRESS, &mut rx_buf, BLOCK) {
            Ok(_) => {
                let size: usize = rx_buf[0] as usize;
                if size > 0 {
                    // See rx_buf for 2 and +2 info.
                    // let as_string = String::from_utf8(rx_buf[2..size+2].to_vec()).expect("Valid string");
                    data = Some(rx_buf[2..size + 2].to_vec())
                }
            }
            Err(_) => {}
        }

        if let Some(incoming_data) = data.clone() {
            if let Ok(maybe_string) = String::from_utf8(incoming_data) {
                if maybe_string.starts_with("WIFI:") {
                    let maybe_creds = parse_wifi_qr(maybe_string);
                    if let Some((ssid, password)) = maybe_creds {
                        log::info!("Trying to connect to wifi -> SSID[{}] Password[{}]", ssid, password);

                        let password = "H0la Chic0s";

                        let mut tries = 3;
                        let mut connected = false;
                        while tries > 0 && !connected {
                            if let Ok(_) = connect_wifi(&mut wifi, &ssid, &String::from(password)) {
                                log::info!("Connected!");
                                connected = true;

                                nvs.set_blob("ssid", ssid.as_bytes()).unwrap();
                                nvs.set_blob("password", password.as_bytes()).unwrap();
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
        }

        match sm.current_state() {
            State::Start => {
                if let Some(incoming_data) = data {
                    log::info!("Scanned data: {:?}", incoming_data.bytes());

                    match subjugated::club::root_as_signed_message(incoming_data.as_slice()) {
                        Ok(signed_msg) => {
                            log::info!("Signed message: {:?}", signed_msg);

                            let signature = signed_msg.signature();
                            println!("Signature: {:?}", signature);

                            if let Some(payload) = signed_msg.payload() {
                                match payload {
                                    Contract => {
                                        // Step 4: Extract the Contract
                                        let contract = signed_msg.payload_as_contract().unwrap();
                                        println!("Contract Public Key: {:?}", contract.public_key().unwrap());

                                        match PublicKey::from_sec1_bytes(contract.public_key().unwrap().bytes()) {
                                            Ok(valid_key) => {
                                                println!("Key correctly parsed!");

                                                let contract_table_start = contract._tab.loc();
                                                let vtable_offset = u16::from_le_bytes(incoming_data[contract_table_start..contract_table_start+2].try_into().expect("wrong size")) as usize;
                                                let contract_end = incoming_data.len();
                                                let vtable_start = contract_table_start - vtable_offset;
                                                let contract_buffer = &incoming_data[vtable_start..contract_end];

                                                log::info!("Contract buffer w/ vtable ({},{}): {:?}", vtable_start, contract_end, contract_buffer.bytes());
                                                let hash = Sha256::digest(&contract_buffer);
                                                log::info!("Hash: {:?}", hash);

                                                let vkey = VerifyingKey::from_sec1_bytes(contract.public_key().unwrap().bytes()).expect("Valid public key");
                                                match Signature::from_bytes(signature.unwrap().bytes().into()) {
                                                    Ok(valid_signature) => {
                                                        log::info!("Signature was valid: {:?}", valid_signature);
                                                        match vkey.verify(&hash, &valid_signature) {
                                                            Ok(_) => {
                                                                println!("Signature verified!");
                                                                if contract.is_partial() {
                                                                    // Go get the other part.
                                                                } else {
                                                                    if contract.is_lock_on_accept() {
                                                                        log::info!("Not partial and Lock on accept!");
                                                                        sm.transition(State::CodeConfirmed);
                                                                    }
                                                                }
                                                            }
                                                            Err(e) => {
                                                                println!("Error verifying signature: {:?}", e);
                                                            }
                                                        }
                                                    }
                                                    Err(e) => {
                                                        println!("Error parsing signature: {:?}", e);
                                                    }
                                                }

                                            }
                                            Err(e) => {
                                                println!("Error parsing key: {:?}", e);
                                            }
                                        }

                                        // Step 5: Extract Capabilities
                                        if let Some(capabilities) = contract.capabilities() {
                                            println!("Capabilities: {:?}", capabilities);
                                        }

                                        // Step 6: Extract is_partial flag
                                        println!("Is Partial: {}", contract.is_partial());
                                    }
                                }
                            }
                        }
                        Err(e) => {
                            println!("Error parsing signed message: {:?}", e);
                        }
                    }
                }
            }
            State::CertificateLoaded => {
                if let Some(incoming_data) = data {
                    if let Ok(maybe_b64_string) = String::from_utf8(incoming_data) {
                        if let Ok(decoded_bytes) = general_purpose::STANDARD.decode(maybe_b64_string) {
                            log::info!("Decoded message: {:?}", decoded_bytes);

                            let nonce : &[u8] = &decoded_bytes[0..12];
                            let nonce_bytes : [u8; 12] = nonce.try_into().expect("wrong size");
                            let nonce: Nonce<Aes256Gcm> = *GenericArray::from_slice(&nonce_bytes);

                            let cipher_text = &decoded_bytes[12..decoded_bytes.len()];
                            if let Some(ref mut actual_cipher) = cipher {
                                match actual_cipher.decrypt(&nonce, Payload { msg: &cipher_text, aad: &[] }) {
                                    Ok(plaintext) => {
                                        let plaintext_str = String::from_utf8_lossy(&plaintext);
                                        println!("Decrypted message: {:?}", plaintext_str);

                                        if plaintext_str == "LOCK" {
                                            sm.transition(State::CodeConfirmed);
                                        }
                                    }
                                    Err(_) => {
                                        println!("Decryption failed! Check key, nonce, or ciphertext.");
                                    }
                                }
                            }
                        }
                    }
                }
            }
            State::CodeConfirmed => {
                log::info!("Locked!");
            }
            State::Reset => {}
        }

        sleep(Duration::from_millis(100));

        // continue; // keep optimizer from removing in --release
    }
}

#[derive(Debug)]
enum State {
    Start,
    CertificateLoaded,
    CodeConfirmed,
    Reset,
}

struct StateMachine {
    state: State,
}

impl StateMachine {
    fn new() -> Self {
        Self { state: State::Start }
    }

    fn transition(&mut self, new_state: State) {
        println!("Transitioning from {:?} to {:?}", self.state, new_state);
        self.state = new_state;
    }

    fn current_state(&self) -> &State {
        &self.state
    }
}

fn connect_wifi(wifi: &mut BlockingWifi<EspWifi<'static>>, ssid: &String, password: &String) -> anyhow::Result<()> {
    let wifi_configuration: Configuration = Configuration::Client(ClientConfiguration {
        ssid: ssid.parse().expect(""),
        bssid: None,
        auth_method: AuthMethod::WPA2Personal,
        password: password.parse().expect(""),
        channel: None,
        scan_method: Default::default(),
        pmf_cfg: Default::default(),
    });
    log::info!("Actual config: {:?}", wifi_configuration);

    wifi.set_configuration(&wifi_configuration)?;

    wifi.connect()?;
    log::info!("Wifi connected");

    wifi.wait_netif_up()?;
    log::info!("Wifi netif up");

    Ok(())
}

fn parse_wifi_qr(qr_code: String) -> Option<(String, String)> {
    if qr_code.starts_with("WIFI:") {
        let mut ssid = String::new();
        let mut password = String::new();

        for part in qr_code.trim_start_matches("WIFI:").split(';') {
            if let Some((key, value)) = part.split_once(':') {
                match key {
                    "S" => ssid = value.to_string(),
                    "P" => password = value.to_string(),
                    _ => {}
                }
            }
        }

        if !ssid.is_empty() {
            return Some((ssid, password));
        }
    }
    None
}