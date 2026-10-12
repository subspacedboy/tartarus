use anyhow::Result;
use esp32_nimble::utilities::BleUuid;
use esp32_nimble::{
    uuid128, BLEAdvertisementData, BLEDevice, DescriptorProperties, NimbleProperties,
};
use log::{error, info};
use std::collections::VecDeque;
use std::sync::Mutex;

// Custom 128-bit UUIDs for Tartarus GATT service & characteristics
pub const TARTARUS_SERVICE_UUID: BleUuid = uuid128!("da0c2439-e9f6-4aac-8948-33031d7ef384");
pub const WIFI_SSID_CHAR_UUID: BleUuid = uuid128!("5d288ec6-65bc-47a6-b4f0-ea4e28547951");
pub const WIFI_PASSWORD_CHAR_UUID: BleUuid = uuid128!("73ced3a6-b6c8-48b4-ab9e-4cec6a83a46e");
pub const COMMAND_CHAR_UUID: BleUuid = uuid128!("fc57b56e-fb30-4227-8a88-cf2b80ccced0");
pub const STATUS_CHAR_UUID: BleUuid = uuid128!("f41280b4-af44-4b14-b5eb-83d4b2a78f35");

#[derive(Debug, Clone)]
pub enum BleEvent {
    SetWifiSsid(String),
    SetWifiPassword(String),
    Command(String), // Arbitrary Base64 command string
}

static EVENT_QUEUE: Mutex<VecDeque<BleEvent>> = Mutex::new(VecDeque::new());
static LAST_SSID: Mutex<Option<String>> = Mutex::new(None);
static LAST_PASSWORD: Mutex<Option<String>> = Mutex::new(None);

/// Pop the next pending BLE event (e.g. WiFi credentials or command) from the queue.
pub fn pop_ble_event() -> Option<BleEvent> {
    if let Ok(mut queue) = EVENT_QUEUE.lock() {
        queue.pop_front()
    } else {
        None
    }
}

/// Helper to drain all pending events.
pub fn drain_ble_events() -> Vec<BleEvent> {
    if let Ok(mut queue) = EVENT_QUEUE.lock() {
        queue.drain(..).collect()
    } else {
        Vec::new()
    }
}

/// Push an incoming event to the thread-safe queue.
fn enqueue_event(event: BleEvent) {
    if let Ok(mut queue) = EVENT_QUEUE.lock() {
        queue.push_back(event);
    }
}

/// Initializes the Tartarus BLE GATT server and begins advertising.
pub fn init_ble_server() -> Result<()> {
    let ble_device = BLEDevice::take();
    let ble_advertising = ble_device.get_advertising();
    let server = ble_device.get_server();

    // Connection lifecycle callbacks
    server.on_connect(|server, desc| {
        info!("BLE client connected: {:?}", desc);
        let _ = server.update_conn_params(desc.conn_handle(), 24, 48, 0, 60);
    });

    let advertising_for_disconnect = ble_device.get_advertising();
    server.on_disconnect(move |_desc, reason| {
        info!("BLE client disconnected ({:?}), restarting advertising...", reason);
        if let Err(e) = advertising_for_disconnect.lock().start() {
            error!("Failed to restart advertising after disconnect: {:?}", e);
        }
    });

    // Create Tartarus service
    let service = server.create_service(TARTARUS_SERVICE_UUID);

    // Characteristic User Description (UUID 0x2901) for human-readable display in nRF Connect
    const USER_DESC_UUID: BleUuid = BleUuid::from_uuid16(0x2901);

    // 1. WiFi SSID Characteristic (READ | WRITE)
    let ssid_char = service.lock().create_characteristic(
        WIFI_SSID_CHAR_UUID,
        NimbleProperties::READ | NimbleProperties::WRITE,
    );
    let ssid_desc = ssid_char
        .lock()
        .create_descriptor(USER_DESC_UUID, DescriptorProperties::READ);
    ssid_desc.lock().set_value(b"WiFi SSID");
    ssid_char.lock().on_write(|args| {
        if let Ok(val) = core::str::from_utf8(args.recv_data()) {
            let ssid = val.trim().to_string();
            info!("BLE received WiFi SSID: '{}'", ssid);
            if let Ok(mut stored) = LAST_SSID.lock() {
                *stored = Some(ssid.clone());
            }
            enqueue_event(BleEvent::SetWifiSsid(ssid));
        } else {
            error!("BLE received invalid UTF-8 for WiFi SSID");
        }
    });

    // 2. WiFi Password Characteristic (WRITE only)
    let pass_char = service.lock().create_characteristic(
        WIFI_PASSWORD_CHAR_UUID,
        NimbleProperties::WRITE,
    );
    let pass_desc = pass_char
        .lock()
        .create_descriptor(USER_DESC_UUID, DescriptorProperties::READ);
    pass_desc.lock().set_value(b"WiFi Password");
    pass_char.lock().on_write(|args| {
        if let Ok(val) = core::str::from_utf8(args.recv_data()) {
            let pass = val.trim().to_string();
            info!("BLE received WiFi password ({} bytes)", pass.len());
            if let Ok(mut stored) = LAST_PASSWORD.lock() {
                *stored = Some(pass.clone());
            }
            enqueue_event(BleEvent::SetWifiPassword(pass));
        } else {
            error!("BLE received invalid UTF-8 for WiFi password");
        }
    });

    // 3. Command Characteristic (WRITE | READ | NOTIFY) - Arbitrary Base64 string
    let cmd_char = service.lock().create_characteristic(
        COMMAND_CHAR_UUID,
        NimbleProperties::READ | NimbleProperties::WRITE | NimbleProperties::NOTIFY,
    );
    cmd_char.lock().set_value(b"ready");
    let cmd_desc = cmd_char
        .lock()
        .create_descriptor(USER_DESC_UUID, DescriptorProperties::READ);
    cmd_desc.lock().set_value(b"Command");
    cmd_char.lock().on_write(|args| {
        if let Ok(val) = core::str::from_utf8(args.recv_data()) {
            let cmd = val.trim().to_string();
            info!("BLE received command string ({} bytes): '{}'", cmd.len(), cmd);
            enqueue_event(BleEvent::Command(cmd));
        } else {
            error!("BLE received invalid UTF-8 for command");
        }
    });

    // 4. Status Characteristic (READ | NOTIFY)
    let status_char = service.lock().create_characteristic(
        STATUS_CHAR_UUID,
        NimbleProperties::READ | NimbleProperties::NOTIFY,
    );
    status_char.lock().set_value(b"idle");
    let status_desc = status_char
        .lock()
        .create_descriptor(USER_DESC_UUID, DescriptorProperties::READ);
    status_desc.lock().set_value(b"Status");

    // Configure Advertising: Device name "Tartarus" and Service UUID
    info!("Starting BLE advertising as 'Tartarus'...");
    ble_advertising.lock().set_data(
        BLEAdvertisementData::new()
            .name("Tartarus")
            .add_service_uuid(TARTARUS_SERVICE_UUID),
    )?;

    ble_advertising.lock().start()?;
    info!("Tartarus BLE GATT server successfully initialized and advertising");

    Ok(())
}
