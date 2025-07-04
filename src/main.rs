extern crate core;

mod acknowledger;
mod config_verifier;
mod configuration_generated;
mod contract_generated;
mod event_generated;
mod fb_helper;
mod firmware_generated;
mod firmware_updater;
mod internal_config;
mod internal_contract;
mod internal_firmware;
mod lock_ctx;
mod lock_state_screen;
mod mqtt_service;
mod overlay;
mod overlays;
mod prelude;
mod qr_screen;
mod screen_ids;
mod screen_state;
mod servo;
mod verifier;
mod wifi_util;

mod firmware_screen;
mod generated;
mod wifi_info_screen;

use crate::servo::Servo;
use esp_idf_hal::ledc::LedcDriver;
use esp_idf_svc::hal::ledc::config::TimerConfig;
use esp_idf_svc::hal::ledc::LedcTimerDriver;
use esp_idf_svc::hal::ledc::Resolution;
use esp_idf_svc::wifi::BlockingWifi;
use esp_idf_svc::wifi::EspWifi;
use mipidsi::models::ST7789;
use mipidsi::options::{ColorInversion, Orientation, Rotation};
use mipidsi::{Builder, Display};
use std::thread::sleep;
use std::time::Duration;

use esp_idf_hal::delay::NON_BLOCK;
use esp_idf_hal::gpio::{AnyIOPin, Gpio40, Gpio41, Output, PinDriver, Pull};
use esp_idf_hal::i2c::{I2cConfig, I2cDriver};
use esp_idf_hal::peripherals::Peripherals;
use esp_idf_hal::prelude::FromValueType;
use esp_idf_hal::spi;
use esp_idf_hal::spi::{SpiDeviceDriver, SpiDriver};
use esp_idf_hal::units::KiloHertz;
use esp_idf_svc::eventloop::EspSystemEventLoop;
use esp_idf_svc::hal::delay;
use esp_idf_svc::nvs::{EspDefaultNvsPartition, EspNvs, NvsDefault};
use esp_idf_svc::sys::{esp_efuse_desc_t, esp_efuse_read_field_bit, esp_random};

use crate::lock_ctx::{LockCtx, TickUpdate};

use crate::wifi_util::connect_wifi;
use embedded_graphics::draw_target::DrawTarget;
use embedded_graphics::geometry::OriginDimensions;
use embedded_graphics::mono_font::ascii::FONT_10X20;
use embedded_graphics::mono_font::MonoTextStyle;
use embedded_graphics::pixelcolor::Rgb565;
use embedded_graphics::prelude::RgbColor;
use embedded_graphics::text::{Alignment, Text};
use embedded_graphics_core::geometry::Point;
use embedded_graphics_core::Drawable;
use mipidsi::interface::SpiInterface;
use rand_core::{CryptoRng, RngCore};

struct Esp32Rng;

impl RngCore for Esp32Rng {
    fn next_u32(&mut self) -> u32 {
        unsafe { esp_random() as u32 }
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

    log::info!("Tartarus Lock V2.0");

    let peripherals = Peripherals::take().expect("Peripherals to be available");

    // Declare all the pins in one spot.
    let mut tft_power = PinDriver::output(peripherals.pins.gpio7).expect("pin to be available");
    let mut tft_rst = PinDriver::output(peripherals.pins.gpio41).expect("to work");
    let mut tft_bl = PinDriver::output(peripherals.pins.gpio45).expect("to work");
    // let tft_dc = peripherals.pins.gpio40;
    let tft_dc: PinDriver<Gpio40, Output> =
        PinDriver::output(peripherals.pins.gpio40).expect("pin to work");
    let tft_cs = peripherals.pins.gpio42;
    let sclk = peripherals.pins.gpio36;
    let sdo = peripherals.pins.gpio35; // Mosi
    let sdi = peripherals.pins.gpio37; // miso

    let servo_pwm_pin = peripherals.pins.gpio18;

    let qr_reader_sda: AnyIOPin = peripherals.pins.gpio3.into();
    let qr_reader_scl: AnyIOPin = peripherals.pins.gpio4.into();

    let mut d0_button = PinDriver::input(peripherals.pins.gpio0).expect("pin to be available");
    let mut d1_button = PinDriver::input(peripherals.pins.gpio1).expect("pin to be available");
    let mut d2_button = PinDriver::input(peripherals.pins.gpio2).expect("pin to be available");

    // Configure buttons
    d0_button.set_pull(Pull::Up).unwrap();
    d1_button.set_pull(Pull::Down).unwrap();
    d2_button.set_pull(Pull::Down).unwrap();

    // Warm up display
    tft_power.set_high().expect("tft power to be high");
    tft_rst.set_low().expect("Reset to be low");
    tft_bl.set_high().expect("BL to be high");

    let spi: SpiDeviceDriver<SpiDriver> = spi::SpiDeviceDriver::new_single(
        peripherals.spi2,
        sclk,
        sdo,
        Some(sdi),
        Some(tft_cs),
        &spi::SpiDriverConfig::new().dma(spi::Dma::Disabled),
        &spi::SpiConfig::new().baudrate(10.MHz().into()),
    )
    .expect("SpiDeviceDriver to work");

    let buffer = Box::leak(Box::new([0_u8; 512]));
    let di = SpiInterface::new(spi, tft_dc, buffer);

    let mut display: Display<
        SpiInterface<SpiDeviceDriver<SpiDriver>, PinDriver<Gpio40, Output>>,
        ST7789,
        PinDriver<Gpio41, Output>,
    > = Builder::new(ST7789, di)
        .reset_pin(tft_rst)
        .invert_colors(ColorInversion::Inverted)
        .display_size(135, 240)
        .display_offset(52, 40)
        .orientation(Orientation::new().rotate(Rotation::Deg90))
        .init(&mut delay::Ets)
        .expect("All to initialize");

    log::info!("Display size: {:?}", display.size());
    display.clear(Rgb565::BLACK).expect("Display to clear");

    let style = MonoTextStyle::new(&FONT_10X20, Rgb565::GREEN);
    let draw_position = Point::new(120, 67);
    let text = Text::with_alignment(
        "@SubspacedBoy\nTartarus Lock Booting",
        draw_position,
        style,
        Alignment::Center,
    );
    text.draw(&mut display).expect("Should have drawn");

    // Warm up NVS (non-volatile storage)
    let nvs_partition =
        EspDefaultNvsPartition::take().expect("EspDefaultNvsPartition to be available");
    let nvs: EspNvs<NvsDefault> = EspNvs::new(nvs_partition.clone(), "storage", true).unwrap();

    // Warming up wifi
    log::info!("Putting wifi on standby");

    let sys_loop = EspSystemEventLoop::take().expect("EspSystemEventLoop to be available");
    let mut wifi: BlockingWifi<EspWifi> = BlockingWifi::wrap(
        EspWifi::new(peripherals.modem, sys_loop.clone(), Some(nvs_partition))
            .expect("EspWifi to work"),
        sys_loop,
    )
    .expect("Wifi to start");
    wifi.start().expect("Wifi should have started");

    // See if we have pre-existing creds
    let mut buffer: [u8; 256] = [0; 256];
    if let Ok(blob) = nvs.get_blob("ssid", &mut buffer) {
        if let Some(actual_ssid) = blob {
            // Ok, we have an SSID. Let's assume we have a matching password.
            let mut pass_buffer: [u8; 256] = [0; 256];
            let pass = nvs
                .get_blob("password", &mut pass_buffer)
                .expect("NVS to work");
            if let Some(actual_pass) = pass {
                let ssid = String::from_utf8(actual_ssid.to_vec()).ok().unwrap();
                let password = String::from_utf8(actual_pass.to_vec()).ok().unwrap();

                if !d2_button.is_high() {
                    if connect_wifi(&mut wifi, &ssid, &password).is_ok() {
                        log::info!("Was able to join wifi from stored credentials :-)");
                    } else {
                        log::info!("Was NOT able to join wifi :-( ");
                    }
                }
                else {
                    log::info!("D2 was held down. Skipping Wi-Fi connect.");
                }

            }
        }
    } else {
        log::info!("No stored wifi credentials. Deferring wifi");
    }

    log::info!("Initializing servo");
    let timer_driver = LedcTimerDriver::new(
        peripherals.ledc.timer0,
        &TimerConfig::default()
            .frequency(50_u32.Hz())
            .resolution(Resolution::Bits14),
    )
    .unwrap();

    let driver: LedcDriver =
        LedcDriver::new(peripherals.ledc.channel0, timer_driver, servo_pwm_pin).unwrap();

    let servo = Servo::new(driver);

    // Checking eFuses

    log::info!("JTAG disabled: {}", is_jtag_disabled());
    log::info!("USB download disabled: {}", is_usb_download_disabled());

    log::info!("Initializing code reader");
    const READER_ADDRESS: u8 = 0x0c;

    let config = I2cConfig::new().baudrate(KiloHertz(400).into());
    let mut i2c_driver = I2cDriver::new(peripherals.i2c0, qr_reader_sda, qr_reader_scl, &config)
        .expect("I2C to initialize");

    // The maximum dataframe size from the tiny code reader is
    // 254 (size of rx_buf). The first byte in the message is the length
    // of the read message with an offset of 2. One for size, and one for
    // the null byte after size.

    log::info!("Ready :-)");
    const SLEEP_DURATION: Duration = Duration::from_millis(150);

    let mut lock_ctx = LockCtx::new(display, nvs, wifi, servo);

    loop {
        let mut rx_buf: [u8; 254] = [0; 254];
        let mut d0_pressed = false;
        let mut d1_pressed = false;
        let mut d2_pressed = false;

        if d0_button.is_low() {
            d0_pressed = true;
        }
        if d1_button.is_high() {
            d1_pressed = true;
        }
        if d2_button.is_high() {
            d2_pressed = true;
        }

        let mut data: Option<Vec<u8>> = None;
        if i2c_driver
            .read(READER_ADDRESS, &mut rx_buf, NON_BLOCK)
            .is_ok()
        {
            let size: usize = rx_buf[0] as usize;
            if size > 0 {
                // See rx_buf for 2 and +2 info.
                data = Some(rx_buf[2..size + 2].to_vec());
            }
        }

        let this_update = TickUpdate {
            d0_pressed,
            d1_pressed,
            d2_pressed,
            qr_data: data,
            since_last: SLEEP_DURATION,
        };

        lock_ctx.tick(this_update);
        sleep(SLEEP_DURATION);

        // continue; // keep optimizer from removing in --release
    }
}

fn is_jtag_disabled() -> bool {
    const JTAG_DISABLE_BIT: u32 = 1 << 24;
    let val = unsafe { core::ptr::read_volatile(0x6001A008 as *const u32) };
    (val & JTAG_DISABLE_BIT) != 0
}

fn is_usb_download_disabled() -> bool {
    const USB_DISABLE_BIT: u32 = 1 << 11;
    let val = unsafe { core::ptr::read_volatile(0x6001A008 as *const u32) };
    (val & USB_DISABLE_BIT) != 0
}
