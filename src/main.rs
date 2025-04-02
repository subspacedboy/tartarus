use std::convert::Infallible;
use std::error::Error;
use std::thread::sleep;
use std::time::Duration;

use aes_gcm::{aead, AeadCore, Aes256Gcm, Key, KeyInit};
use aes_gcm::aead::{Aead, Nonce, Payload};
use aes_gcm::aead::generic_array::GenericArray;
use base64::{decode, encode, Engine};
use base64::engine::general_purpose;
use display_interface_spi::SPIInterfaceNoCS;
use embedded_graphics::Drawable;
use embedded_graphics::geometry::Point;
use embedded_graphics::pixelcolor::Rgb565;
use embedded_graphics::prelude::*;
use embedded_graphics::primitives::{Circle, Primitive, PrimitiveStyle};
use embedded_graphics::primitives::*;
use embedded_hal::spi::MODE_3;
use embedded_hal_compat::eh0_2::digital::v1_compat::OldOutputPin;
use embedded_hal_compat::ReverseCompat;
use esp_idf_hal::gpio::{AnyIOPin, AnyOutputPin, OutputPin, PinDriver};
use esp_idf_hal::io::{ErrorType, Read};
use esp_idf_hal::peripherals::Peripherals;
use esp_idf_hal::prelude::FromValueType;
use esp_idf_hal::spi;
use esp_idf_hal::spi::{Spi, SpiConfig, SpiDeviceDriver, SpiDriver};
use esp_idf_hal::spi::config::DriverConfig;
use esp_idf_svc::hal::delay;
use esp_idf_svc::hal::gpio;
use esp_idf_svc::sys::esp_random;
use esp_idf_svc::nvs::{EspNvs, EspNvsPartition, NvsDefault};
use hkdf::Hkdf;
use p256::{ecdh, PublicKey, SecretKey, U32};
use p256::ecdh::{diffie_hellman, SharedSecret};
use p256::ecdsa::{Signature, SigningKey, VerifyingKey};
use p256::ecdsa::signature::Signer;
use p256::pkcs8::{DecodePublicKey, EncodePublicKey, LineEnding};
use qrcode::{Color, QrCode};
use rand_core::{CryptoRng, RngCore};
use sha2::Sha256;
use st7789::{Orientation, ST7789};
use typenum::U12;


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

fn main() {
    // It is necessary to call this function once. Otherwise some patches to the runtime
    // implemented by esp-idf-sys might not link properly. See https://github.com/esp-rs/esp-idf-template/issues/71
    esp_idf_svc::sys::link_patches();

    // Bind the log crate to the ESP Logging facilities
    esp_idf_svc::log::EspLogger::initialize_default();

    log::info!("Hello, world!");

    let peripherals = Peripherals::take().expect("Peripherals to be available");

    let mut config = SpiConfig::default();
    config.baudrate = 10_000_000.Hz();
    config.data_mode = MODE_3;

    let mut config1 = DriverConfig::default();

    let mut tft_power = PinDriver::output(peripherals.pins.gpio21).expect("pin to be available");
    tft_power.set_high().expect("tft power to be high");

    let sclk = peripherals.pins.gpio36;
    let sdo = peripherals.pins.gpio35;// Mosi
    let sdi = peripherals.pins.gpio37;
    // let rst = PinDriver::output(/**/).expect("Pin to be available");
    // let rst = peripherals.pins.gpio40.downgrade_output();
    let mut rst = PinDriver::output(peripherals.pins.gpio40).expect("to work");
    rst.set_low().expect("Reset to be low");
    let mut old_rst = OldOutputPin::new(rst);
    // let old_rst = OldOutputPin::new(rst);
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

    // let circle1 =
    //     Circle::new(Point::new(128, 64), 64).into_styled(PrimitiveStyle::with_fill(Rgb565::RED));
    //
    // circle1.draw(&mut display).expect("Circle to be drawn");

    let partition = EspNvsPartition::<NvsDefault>::take().unwrap();
    let mut nvs = EspNvs::new(partition, "storage", true).unwrap();

    let key_name = "ec_private";
    let mut buffer : [u8; 256] = [0; 256];
    let existing_key: Option<&[u8]> = nvs.get_blob(key_name, &mut buffer).ok().expect("The NVS mechanism should have worked");
    let mut rng = Esp32Rng;

    let private_key : SecretKey = if let Some(base64_key) = existing_key {
        log::info!("Loaded existing EC private key.");
        let key_bytes = decode(base64_key).unwrap();
        SecretKey::from_slice(&key_bytes).expect("Failed to parse private key")
    } else {
        log::info!("Generating a new EC private key...");
        let sk = SecretKey::random(&mut rng);

        // // Serialize & encode in base64
        let key_bytes = sk.to_bytes();
        let encoded_key = encode(key_bytes);
        nvs.set_blob(key_name, encoded_key.as_bytes()).unwrap();
        log::info!("Saved new EC private key to NVS.");
        sk
    };

    let my_public = PublicKey::from_secret_scalar(&private_key.to_nonzero_scalar());

    // Simulate receiver's key pair
    // let receiver_secret = SecretKey::random(&mut rng);
    // let receiver_public = PublicKey::from_secret_scalar(&receiver_secret.to_nonzero_scalar());

    const REMOTE_PUB_KEY: &str = "LS0tLS1CRUdJTiBQVUJMSUMgS0VZLS0tLS0KTUZrd0V3WUhLb1pJemowQ0FRWUlLb1pJemowREFRY0RRZ0FFa3dVUDJmbjhMQVJkYi8rSDZrMFJzMEtaeE4zUApsTHVlTzQxdHFPRktGZlFWeC9CcHpVNGdYSmYzbEdKTjFDdXBGMlExbVcralV1cVJuMlpvSS82U3VBPT0KLS0tLS1FTkQgUFVCTElDIEtFWS0tLS0tCg==";
    let pub_key = String::from_utf8(general_purpose::STANDARD.decode(REMOTE_PUB_KEY).expect("Key didn't parse")).expect("String to be valid");
    let receiver_public = PublicKey::from_public_key_pem(pub_key.as_str()).expect("Valid key");

    let shared_secret = diffie_hellman(
        private_key.to_nonzero_scalar(),
        receiver_public.as_affine()
    );

    // let symmetric_key: &Key<Aes256Gcm> = Key::from_slice(shared_secret.raw_secret_bytes()).into();

    let hk = Hkdf::<Sha256>::new(None, &shared_secret.raw_secret_bytes());
    let mut aes_key = [0u8; 32]; // Buffer for AES-256 key
    const NONCE: &str = "8w/3FFyYUcwwKxnp";
    const CIPHER_TEXT: &str = "jiAH8/ExuAumT476Z/OC/Jc3dsM=";
    hk.expand("AES-GCM key derivation".as_bytes(), &mut aes_key).expect("HKDF expansion failed");
    log::info!("Derived key: {:x?}", aes_key);

    // let symmetric_key: &Key<Aes256Gcm> = shared_secret.raw_secret_bytes().into();
    let symmetric_key: &Key<Aes256Gcm> = (&aes_key).into();
    let cipher = Aes256Gcm::new(symmetric_key);

    let nonce = general_purpose::STANDARD.decode(NONCE).expect("Nonce didn't parse");
    let nonce_bytes : [u8; 12] = nonce.try_into().expect("wrong size");
    let nonce: Nonce<Aes256Gcm> = *GenericArray::from_slice(&nonce_bytes);

    let cipher_text = general_purpose::STANDARD.decode(CIPHER_TEXT).expect("Nonce didn't parse");

    log::info!("ciphertext : {:x?}", cipher_text);
    match cipher.decrypt(&nonce, Payload { msg: &cipher_text, aad: &[] }) {
        Ok(plaintext) => {
            println!("Decrypted message: {:?}", String::from_utf8_lossy(&plaintext));
        }
        Err(_) => {
            println!("Decryption failed! Check key, nonce, or ciphertext.");
        }
    }

    // cchandler: These are for encryption only.
    // let nonce = Aes256Gcm::generate_nonce(&mut rng);
    // let message = b"internal state";
    // let cipher_text = cipher.encrypt(&nonce, message.as_ref()).expect("Encryption failure!");

    // let signing_key = SigningKey::random(&mut rng);
    // let verifying_key = VerifyingKey::from(&signing_key);
    // let mut signature: Signature = signing_key.sign(message);

    // let thingy = signature.as_ref();
    // let whole_message = format!("http://192.168.1.168:5002?public={}", my_public.to_public_key_pem(LineEnding::CR).unwrap(), String::from_utf8_lossy(&cipher_text));

    let encoded_cert = general_purpose::STANDARD.encode(&my_public.to_public_key_pem(LineEnding::CR).unwrap());
    let whole_message = format!("http://192.168.1.168:5002/?public={}", encoded_cert);
    // let whole_message = format!("public=asdf");

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

    // Draw the circle on the display

    log::info!("Ready :-)");

    loop {
        sleep(Duration::from_millis(100));

        // continue; // keep optimizer from removing in --release
    }
}
