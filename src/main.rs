extern crate core;

mod acknowledger;
pub mod ble_server;
mod config_verifier;
#[allow(unused_imports, mismatched_lifetime_syntaxes)]
mod configuration_generated;
#[allow(unused_imports, mismatched_lifetime_syntaxes)]
mod contract_generated;
mod event_buffer;
#[allow(unused_imports, mismatched_lifetime_syntaxes)]
mod event_generated;
mod fb_helper;
#[allow(unused_imports, mismatched_lifetime_syntaxes)]
mod firmware_generated;
mod firmware_updater;
mod generated;
mod generated_ui;
mod internal_config;
mod internal_contract;
mod internal_firmware;
mod lock_ctx;
mod mqtt_service;
mod prelude;
mod screen_ids;
use screen_ids::ScreenId;
mod servo;
mod slint_platform;
mod verifier;
mod wifi_manager;
mod wifi_util;

use crate::wifi_manager::WifiManager;
use std::io::Read;
use std::sync::mpsc::{channel, Receiver};
use std::sync::Mutex;
use crate::wifi_manager::WIFI_MANAGER;
use crate::servo::Servo;
use esp_idf_hal::ledc::LedcDriver;
use esp_idf_svc::hal::ledc::config::TimerConfig;
use esp_idf_svc::hal::ledc::LedcTimerDriver;
use esp_idf_svc::hal::ledc::Resolution;
use mipidsi::models::ST7789;
use mipidsi::options::{ColorInversion, Orientation, Rotation};
use mipidsi::Builder;
use std::thread::sleep;
use std::time::Duration;

use esp_idf_hal::delay::TickType;
use esp_idf_hal::gpio::{AnyIOPin, Output, PinDriver, Pull};
use esp_idf_hal::i2c::{I2cConfig, I2cDriver};
use esp_idf_hal::peripherals::Peripherals;
use esp_idf_hal::units::FromValueType;
use esp_idf_hal::spi;
use esp_idf_hal::spi::{SpiDeviceDriver, SpiDriver};
use esp_idf_hal::units::KiloHertz;
use esp_idf_svc::eventloop::EspSystemEventLoop;
use esp_idf_svc::hal::delay;
use esp_idf_svc::nvs::{EspDefaultNvsPartition, EspNvs, NvsDefault};
use esp_idf_svc::sys::{
    esp_efuse_read_field_bit, esp_random, ESP_EFUSE_DIS_PAD_JTAG,
    ESP_EFUSE_DIS_USB_SERIAL_JTAG_DOWNLOAD_MODE, ESP_EFUSE_SECURE_BOOT_EN,
};

use crate::lock_ctx::{LockCtx, TickUpdate};

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

    // Runtime sanity check: Verify partition table and NVS geometry before touching storage
    verify_partition_table_and_nvs();

    let peripherals = Peripherals::take().expect("Peripherals to be available");

    // Declare all the pins in one spot.
    let mut tft_power = PinDriver::output(peripherals.pins.gpio7).expect("pin to be available");
    let mut tft_rst = PinDriver::output(peripherals.pins.gpio41).expect("to work");
    let mut tft_bl = PinDriver::output(peripherals.pins.gpio45).expect("to work");
    let tft_dc: PinDriver<Output> =
        PinDriver::output(peripherals.pins.gpio40).expect("pin to work");
    let tft_cs = peripherals.pins.gpio42;
    let sclk = peripherals.pins.gpio36;
    let sdo = peripherals.pins.gpio35; // Mosi
    let sdi = peripherals.pins.gpio37; // miso

    let servo_pwm_pin = peripherals.pins.gpio18;

    let qr_reader_sda: AnyIOPin = peripherals.pins.gpio3.into();
    let qr_reader_scl: AnyIOPin = peripherals.pins.gpio4.into();

    let d0_button = PinDriver::input(peripherals.pins.gpio0, Pull::Up).expect("pin to be available");
    let d1_button = PinDriver::input(peripherals.pins.gpio1, Pull::Down).expect("pin to be available");
    let d2_button = PinDriver::input(peripherals.pins.gpio2, Pull::Down).expect("pin to be available");

    // Make sure to configure WiFi before enabling the TFT backlight.
    // Warming up wifi
    log::info!("Putting wifi on standby");

    // Warm up NVS (non-volatile storage)
    let nvs_partition =
        EspDefaultNvsPartition::take().expect("EspDefaultNvsPartition to be available");
    log_nvs_health_stats();
    let nvs: EspNvs<NvsDefault> = EspNvs::new(nvs_partition.clone(), "storage", true).unwrap();

    let sys_loop = EspSystemEventLoop::take().expect("EspSystemEventLoop to be available");

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
        &spi::SpiConfig::new().baudrate(10_u32.MHz().into()),
    )
    .expect("SpiDeviceDriver to work");

    // SPI display buffer for the ST7789 display interface.
    //
    // CONTEXT & MEMORY ARCHITECTURE:
    // This buffer is deliberately allocated as a static array in `.bss` rather than
    // via heap allocation (`Box::leak`). On ESP32-S3 systems with PSRAM enabled:
    // 1. General heap allocations can be placed in external PSRAM, but SPI peripheral
    //    hardware and high-speed display transmission pipelines require buffers to reside
    //    in internal SRAM (DRAM). Placing it in `.bss` guarantees internal SRAM placement.
    // 2. Pre-allocating at link-time avoids runtime heap fragmentation on embedded boots.
    //
    // In Rust 1.84+ and the Rust 2024 edition, taking a direct mutable reference (`&mut SPI_BUFFER`)
    // triggers `static_mut_refs`. We use `addr_of_mut!` to construct a raw pointer and dereference
    // it once into the required `&'static mut [u8; 512]` reference without aliasing hazards.
    static mut SPI_BUFFER: [u8; 512] = [0; 512];
    let buffer: &'static mut [u8; 512] = unsafe { &mut *core::ptr::addr_of_mut!(SPI_BUFFER) };
    let di = SpiInterface::new(spi, tft_dc, buffer);

    let mut display: crate::prelude::MyDisplay = Builder::new(ST7789, di)
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

    let mutex = WIFI_MANAGER.get_or_init(|| {
        log::info!("Initializing wifi manager");
        let manager = WifiManager::new(nvs_partition.clone(), peripherals.modem, sys_loop);
        Mutex::new(manager)
    });

    let mut manager = mutex.lock().unwrap();
    let init_result = manager.process_initial_state(d2_button.is_high());
    match init_result {
        Ok(state) => {
            log::info!("Wifi manager initialized -> {:?}", state);
        }
        Err(e) => {
            log::info!("Wifi manager errored on initialize: {:?}", e);
        }
    }
    drop(manager);

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

    log::info!("Secure boot enabled: {}", is_secure_boot_enabled());
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

    let (window, ui) = slint_platform::init_slint_platform();
    let stdin_rx = start_stdin_reader();
    let mut lock_ctx = LockCtx::new(nvs, servo);

    // Initial QR code load
    let mut last_rendered_qr_url: Option<String> = None;
    if !lock_ctx.needs_configuration() {
        if let Ok(url) = lock_ctx.get_lock_url() {
            if let Some(qr_img) = slint_platform::generate_qr_image(url) {
                ui.set_qr_code_image(qr_img);
                last_rendered_qr_url = Some(url.to_string());
            }
        }
    }

    if let Ok(version) = lock_ctx.firmware_manager.get_running_version() {
        ui.set_firmware_version(version.into());
    }

    let mut last_contract_serial: Option<u16> = None;
    let mut last_wifi_connected: Option<bool> = None;
    let mut last_update_avail: Option<bool> = None;

    // Generate initial random 6-digit PIN for BLE authentication
    let initial_pin_num = unsafe { esp_random() } % 1_000_000;
    let initial_pin = format!("{:06}", initial_pin_num);
    ble_server::set_auth_pin(&initial_pin);
    ui.set_ble_pin(initial_pin.into());
    log::info!("Initial BLE pairing PIN generated: {}", ble_server::get_auth_pin());

    // Initialize Tartarus BLE GATT Server
    if let Err(e) = ble_server::init_ble_server() {
        log::error!("Failed to initialize Tartarus BLE GATT server: {:?}", e);
    }

    loop {
        // Process any incoming BLE events (WiFi credentials or Base64 commands)
        while let Some(ble_event) = ble_server::pop_ble_event() {
            match ble_event {
                ble_server::BleEvent::SetWifiSsid(ssid) => {
                    log::info!("BLE updating WiFi SSID: {}", ssid);
                    if let Some(mgr_cell) = crate::wifi_manager::WIFI_MANAGER.get() {
                        let should_connect = {
                            let mut mgr = mgr_cell.lock().unwrap();
                            mgr.ssid = Some(ssid);
                            mgr.password.is_some()
                        };
                        if should_connect {
                            crate::wifi_manager::trigger_async_connect();
                        }
                    }
                }
                ble_server::BleEvent::SetWifiPassword(pass) => {
                    log::info!("BLE updating WiFi password");
                    if let Some(mgr_cell) = crate::wifi_manager::WIFI_MANAGER.get() {
                        let should_connect = {
                            let mut mgr = mgr_cell.lock().unwrap();
                            mgr.password = Some(pass);
                            mgr.ssid.is_some()
                        };
                        if should_connect {
                            crate::wifi_manager::trigger_async_connect();
                        }
                    }
                }
                ble_server::BleEvent::Command(cmd_str) => {
                    log::info!("BLE executing command ({} bytes)", cmd_str.len());
                    lock_ctx.process_qr_payload(cmd_str.as_bytes());
                }
                ble_server::BleEvent::ClearEventQueue => {
                    log::info!("BLE request: clearing event queue");
                    let cleared = lock_ctx.clear_event_queue();
                    let msg = format!("cleared {} events", cleared);
                    ble_server::set_event_queue_ctrl_status(&msg);
                }
                ble_server::BleEvent::ReenqueueEvents => {
                    log::info!("BLE request: force re-enqueuing events from flash");
                    let count = lock_ctx.force_reenqueue_events();
                    let msg = format!("reenqueued {} events", count);
                    ble_server::set_event_queue_ctrl_status(&msg);
                    lock_ctx.update_ble_event_queue_status();
                }
                ble_server::BleEvent::RefreshEventQueue => {
                    log::info!("BLE request: refreshing event queue status");
                    let count = lock_ctx.event_ring.meta().count;
                    let msg = format!("refreshed: {} events", count);
                    ble_server::set_event_queue_ctrl_status(&msg);
                    lock_ctx.update_ble_event_queue_status();
                }
            }
        }

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

        if let Ok(stdin_payload) = stdin_rx.try_recv() {
            log::info!("Injecting STDIN payload into qr_data: {} bytes", stdin_payload.len());
            data = Some(stdin_payload);
        } else if i2c_driver.read(READER_ADDRESS, &mut rx_buf, TickType::new_millis(25).ticks()).is_ok() {
            // 1. Read the 16-bit little-endian length properly
            let size = u16::from_le_bytes([rx_buf[0], rx_buf[1]]) as usize;

            // 2. Sanity check the size (TCR max payload is 254 bytes)
            if size > 0 && size <= 254 {
                let payload = &rx_buf[2..size + 2];

                // 3. Strip any null bytes caused by torn reads or misaligned buffers
                let clean_payload: Vec<u8> = payload
                    .iter()
                    .copied()
                    .take_while(|&b| b != 0) // Stop at the first null terminator
                    .collect();

                data = Some(clean_payload);
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

        if lock_ctx.take_unlock_event() {
            ui.invoke_trigger_unlock_blink();
        }

        // Sync state to Slint UI
        ui.set_active_screen(lock_ctx.current_screen as i32);
        ui.set_screen_title(lock_ctx.current_screen.title().into());
        ui.set_is_emergency_screen_available(lock_ctx.is_emergency_unlock_available());
        ui.set_emergency_unlocked(lock_ctx.emergency_unlocked);
        ui.set_emergency_press_count(lock_ctx.emergency_press_count as i32);
        ui.set_is_locked(lock_ctx.is_locked());
        ui.set_needs_config(lock_ctx.needs_configuration());
        ui.set_ble_pin(ble_server::get_auth_pin().into());
        ui.set_ble_authenticated(ble_server::is_authenticated());
        ui.set_ble_locked_out(ble_server::is_locked_out());

        let current_contract_serial = lock_ctx.contract.as_ref().map(|c| c.serial_number);
        if current_contract_serial != last_contract_serial {
            last_contract_serial = current_contract_serial;
            if let Some(serial) = current_contract_serial {
                ui.set_contract_id(serial.to_string().into());
            } else {
                ui.set_contract_id("".into());
            }
        }

        let is_connected = if let Some(m) = WIFI_MANAGER.get() {
            if let Ok(guard) = m.try_lock() {
                guard.is_connected()
            } else {
                false
            }
        } else {
            false
        };
        if last_wifi_connected != Some(is_connected) {
            last_wifi_connected = Some(is_connected);
            ui.set_wifi_connected(is_connected);
            if let Ok(ssid) = lock_ctx.get_wifi_ssid() {
                ui.set_wifi_ssid(ssid.into());
            }
        }

        let update_avail = lock_ctx.firmware_manager.is_update_available();
        if last_update_avail != Some(update_avail) {
            last_update_avail = Some(update_avail);
            ui.set_firmware_upgrade_available(update_avail);
            if update_avail {
                ui.set_next_firmware_version(lock_ctx.firmware_manager.next_firmware_version().into());
            }
        }

        let btn_indicator = if d0_pressed {
            1
        } else if d1_pressed {
            2
        } else if d2_pressed {
            3
        } else {
            0
        };
        ui.set_button_indicator(btn_indicator);

        // Refresh QR code if needed
        if lock_ctx.current_screen == ScreenId::QrCode && !lock_ctx.needs_configuration() {
            if let Ok(url) = lock_ctx.get_lock_url() {
                if last_rendered_qr_url.as_deref() != Some(url) {
                    if let Some(qr_img) = slint_platform::generate_qr_image(url) {
                        ui.set_qr_code_image(qr_img);
                        last_rendered_qr_url = Some(url.to_string());
                    }
                }
            }
        }

        // Render Slint UI
        slint::platform::update_timers_and_animations();
        window.draw_if_needed(|renderer| {
            renderer.render_by_line(slint_platform::DisplayWrapper {
                display: &mut display,
                line_buffer: [slint::platform::software_renderer::Rgb565Pixel(0); 240],
            });
        });

        sleep(SLEEP_DURATION);
    }
}

fn verify_partition_table_and_nvs() {
    const EXPECTED_NVS_OFFSET: u32 = 0x9000;
    const EXPECTED_NVS_SIZE: u32 = 0x6000; // 24 KiB

    log::info!("=== Partition Table Sanity Check ===");

    // Enumerate and log all partitions in flash
    let mut it = unsafe {
        esp_idf_svc::sys::esp_partition_find(
            esp_idf_svc::sys::esp_partition_type_t_ESP_PARTITION_TYPE_ANY,
            esp_idf_svc::sys::esp_partition_subtype_t_ESP_PARTITION_SUBTYPE_ANY,
            core::ptr::null(),
        )
    };

    while !it.is_null() {
        let p_ptr = unsafe { esp_idf_svc::sys::esp_partition_get(it) };
        if !p_ptr.is_null() {
            let p = unsafe { &*p_ptr };
            let p_label = unsafe {
                core::ffi::CStr::from_ptr(p.label.as_ptr())
                    .to_str()
                    .unwrap_or("<unknown>")
            };
            log::info!(
                "  |-- [{:<10}] offset: 0x{:05x} - 0x{:05x} ({:>4} KiB) [type=0x{:02x}, subtype=0x{:02x}, enc={}]",
                p_label,
                p.address,
                p.address + p.size,
                p.size / 1024,
                p.type_,
                p.subtype,
                p.encrypted,
            );
        }
        // In ESP-IDF, esp_partition_next() automatically releases and frees
        // the iterator when reaching the end of the list and returning NULL.
        it = unsafe { esp_idf_svc::sys::esp_partition_next(it) };
    }

    // Verify NVS partition specifically
    let nvs_ptr = unsafe {
        esp_idf_svc::sys::esp_partition_find_first(
            esp_idf_svc::sys::esp_partition_type_t_ESP_PARTITION_TYPE_DATA,
            esp_idf_svc::sys::esp_partition_subtype_t_ESP_PARTITION_SUBTYPE_DATA_NVS,
            core::ptr::null(),
        )
    };

    if nvs_ptr.is_null() {
        log::error!("CRITICAL: NVS partition not found in active partition table!");
        panic!("FATAL: NVS partition not found in partition table");
    }

    let nvs = unsafe { &*nvs_ptr };
    let nvs_label = unsafe {
        core::ffi::CStr::from_ptr(nvs.label.as_ptr())
            .to_str()
            .unwrap_or("<unknown>")
    };

    if nvs.address != EXPECTED_NVS_OFFSET || nvs.size != EXPECTED_NVS_SIZE {
        log::error!(
            "FATAL GEOMETRY MISMATCH: NVS partition '{}' is at offset 0x{:05x} with size 0x{:x} ({} KiB)!",
            nvs_label,
            nvs.address,
            nvs.size,
            nvs.size / 1024,
        );
        log::error!(
            "Expected exactly 24 KiB (0x{:x}) at offset 0x{:05x}.",
            EXPECTED_NVS_SIZE,
            EXPECTED_NVS_OFFSET
        );
        log::error!("Halting boot immediately to prevent NVS reformat/wipe and credential loss!");
        panic!(
            "NVS geometry mismatch: found {} KiB at 0x{:05x}, expected 24 KiB at 0x{:05x}",
            nvs.size / 1024,
            nvs.address,
            EXPECTED_NVS_OFFSET
        );
    }

    log::info!(
        "✅ NVS partition geometry verified OK: '{}' at 0x{:05x} ({} KiB)",
        nvs_label,
        nvs.address,
        nvs.size / 1024
    );
}

fn log_nvs_health_stats() {
    let mut stats = esp_idf_svc::sys::nvs_stats_t {
        used_entries: 0,
        free_entries: 0,
        available_entries: 0,
        total_entries: 0,
        namespace_count: 0,
    };

    let err = unsafe {
        esp_idf_svc::sys::nvs_get_stats(core::ptr::null(), &mut stats as *mut _)
    };

    if err == esp_idf_svc::sys::ESP_OK {
        log::info!(
            "NVS Health Stats: used={}/{} entries, free={}, available={}, namespaces={}",
            stats.used_entries,
            stats.total_entries,
            stats.free_entries,
            stats.available_entries,
            stats.namespace_count,
        );
    } else {
        log::warn!("Could not query NVS health stats: ESP-IDF error 0x{:x}", err);
    }
}

fn is_secure_boot_enabled() -> bool {
    unsafe {
        esp_efuse_read_field_bit(core::ptr::addr_of_mut!(ESP_EFUSE_SECURE_BOOT_EN) as *mut _)
    }
}

fn is_jtag_disabled() -> bool {
    unsafe {
        esp_efuse_read_field_bit(core::ptr::addr_of_mut!(ESP_EFUSE_DIS_PAD_JTAG) as *mut _)
    }
}

fn is_usb_download_disabled() -> bool {
    unsafe {
        esp_efuse_read_field_bit(
            core::ptr::addr_of_mut!(ESP_EFUSE_DIS_USB_SERIAL_JTAG_DOWNLOAD_MODE) as *mut _,
        )
    }
}

fn start_stdin_reader() -> Receiver<Vec<u8>> {
    let (tx, rx) = channel();

    std::thread::Builder::new()
        .stack_size(4096)
        .name("stdin-reader".to_string())
        .spawn(move || {
            let mut stdin = std::io::stdin();
            let mut buffer: Vec<u8> = Vec::new();
            let mut byte_buf = [0u8; 64];

            loop {
                match stdin.read(&mut byte_buf) {
                    Ok(0) => {
                        sleep(Duration::from_millis(50));
                    }
                    Ok(n) => {
                        for &b in &byte_buf[..n] {
                            match b {
                                b'\r' | b'\n' => {
                                    if !buffer.is_empty() {
                                        log::info!(
                                            "STDIN received line ({} bytes): {:?}",
                                            buffer.len(),
                                            String::from_utf8_lossy(&buffer)
                                        );
                                        let _ = tx.send(buffer.clone());
                                        buffer.clear();
                                    }
                                }
                                0x08 | 0x7F => {
                                    buffer.pop();
                                }
                                other => {
                                    buffer.push(other);
                                }
                            }
                        }
                    }
                    Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                        sleep(Duration::from_millis(20));
                    }
                    Err(e) => {
                        log::warn!("STDIN read error: {:?}", e);
                        sleep(Duration::from_millis(100));
                    }
                }
            }
        })
        .expect("Failed to spawn stdin reader thread");

    rx
}

