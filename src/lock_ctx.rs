use base64::Engine;
use base64::engine::general_purpose;
use display_interface_spi::SPIInterface;
use esp_idf_hal::gpio::{Gpio40, Gpio41, Gpio45, GpioError, Output, PinDriver};
use esp_idf_hal::spi::{SpiDeviceDriver, SpiDriver};
use esp_idf_svc::nvs::{EspNvs, NvsDefault};
use p256::{PublicKey, SecretKey};
use p256::pkcs8::{EncodePublicKey, LineEnding};
use st7789::ST7789;
use crate::boot_screen::BootScreen;
use crate::Esp32Rng;
use crate::prelude::prelude;
use crate::prelude::prelude::{DynScreen, MyDisplay, MySPI};
use crate::screen_state::ScreenState;

pub struct LockCtx {
    display: MyDisplay<'static>,
    nvs: EspNvs<NvsDefault>,
    current_screen: Option<Box<DynScreen<'static>>>,
    secret_key: Option<SecretKey>,
    public_key: Option<PublicKey>,
}

impl LockCtx {
    pub fn new(display: MyDisplay<'static>, nvs: EspNvs<NvsDefault>) -> Self {
        let mut lck = LockCtx {
            display,
            nvs,
            current_screen: None,
            secret_key: None,
            public_key: None
        };

        let secret_key = lck.load_or_create_key();
        let my_public = PublicKey::from_secret_scalar(&secret_key.to_nonzero_scalar());

        //TODO: Do something with this encoded body?
        let encoded_cert = general_purpose::STANDARD.encode(&my_public.to_public_key_pem(LineEnding::CR).unwrap());

        lck.secret_key = Some(secret_key);
        lck.public_key = Some(my_public);

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

    pub fn tick(&mut self) -> () {
        if let Some(current_screen) = self.current_screen.as_mut() {
            if current_screen.needs_redraw() {
                current_screen.draw_screen(&mut self, &mut self.display);
            }
        }
    }
}