use base64::Engine;
use base64::engine::general_purpose;
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
use esp_idf_svc::nvs::{EspNvs, NvsDefault};
use esp_idf_svc::wifi::{BlockingWifi, EspWifi};
use p256::{PublicKey, SecretKey};
use p256::pkcs8::{EncodePublicKey, LineEnding};
use st7789::ST7789;
use crate::boot_screen::BootScreen;
use crate::Esp32Rng;
use crate::overlays::WifiOverlay;
use crate::prelude::prelude;
use crate::prelude::prelude::{DynOverlay, DynScreen, MyDisplay, MySPI};
use crate::screen_state::ScreenState;
use crate::wifi_util::{connect_wifi, parse_wifi_qr};

pub struct TickUpdate {
    pub d0_pressed : bool,
    pub d1_pressed : bool,
    pub d2_pressed : bool,
    pub qr_data : Option<Vec<u8>>
}

pub struct LockCtx {
    pub(crate) display: MyDisplay<'static>,
    nvs: EspNvs<NvsDefault>,
    current_screen: Option<Box<DynScreen<'static>>>,
    overlays: Vec<Box<DynOverlay<'static>>>,
    wifi: BlockingWifi<EspWifi<'static>>,
    pub(crate) wifi_connected: bool,
    secret_key: Option<SecretKey>,
    pub(crate) public_key: Option<PublicKey>,
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
            public_key: None
        };

        if let Ok(connected) = lck.wifi.is_connected() {
            lck.wifi_connected = connected;
        }

        let secret_key = lck.load_or_create_key();
        let my_public = PublicKey::from_secret_scalar(&secret_key.to_nonzero_scalar());

        //TODO: Do something with this encoded body?
        let encoded_cert = general_purpose::STANDARD.encode(&my_public.to_public_key_pem(LineEnding::CR).unwrap());

        lck.secret_key = Some(secret_key);
        lck.public_key = Some(my_public);

        let wifi_overlay: Box<DynOverlay<'static>> = Box::new(
            WifiOverlay::<
                MySPI<'static>,
                PinDriver<'static, _, Output>,
                PinDriver<'static, _, Output>,
                GpioError
            >::new()
        );
        lck.overlays.push(wifi_overlay);

        let boot_screen: Box<DynScreen<'static>> = Box::new(
            BootScreen::<
                MySPI<'static>,
                PinDriver<'static, _, Output>,
                PinDriver<'static, _, Output>,
                GpioError
            >::new()
        );
        lck.current_screen = Some(boot_screen);

        lck
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
        if let Some(mut current_screen) = self.current_screen.take() {
            if current_screen.needs_redraw() {
                current_screen.draw_screen(self);
            }
            self.current_screen = Some(current_screen);
        }

        let mut overlays = core::mem::take(&mut self.overlays);
        for overlay in overlays.iter_mut() {
            overlay.draw_screen(self);
        }
        self.overlays = overlays;

        if update.d0_pressed {
            let blue_square = Rectangle::new(Point::new(0, 0), Size::new(25, 25))
                .into_styled(PrimitiveStyleBuilder::new().fill_color(Rgb565::BLUE).build());
            blue_square.draw(&mut self.display).expect("Failed to draw screen");

            log::info!("Drew blue square");
        } else {
            let white_square = Rectangle::new(Point::new(0, 0), Size::new(25, 25))
                .into_styled(PrimitiveStyleBuilder::new().fill_color(Rgb565::WHITE).build());
            white_square.draw(&mut self.display).expect("Failed to draw screen");
            white_square.draw(&mut self.display).expect("Failed to draw screen");
            // let r = Rectangle::new(Point::new(0, 0), Size::new(50, 50));
            // self.display.fill_solid(&r, Rgb565::WHITE).unwrap();
            // log::info!("Drew white square");
        }

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
        }
    }
}