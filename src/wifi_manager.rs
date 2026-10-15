use core::sync::atomic::{AtomicBool, Ordering};
use std::cmp::PartialEq;
use std::sync::{Arc, Mutex, OnceLock};
use std::time::{Duration, Instant};

use embedded_svc::wifi::{
    AuthMethod, ClientConfiguration, Configuration, PmfConfiguration, ScanMethod, ScanSortMethod,
};
use esp_idf_hal::modem;
use esp_idf_hal::sys::{
    esp_wifi_set_country, esp_wifi_set_ps, esp_wifi_set_storage, wifi_country_t,
    wifi_ps_type_t_WIFI_PS_NONE, wifi_storage_t_WIFI_STORAGE_RAM,
};
use esp_idf_svc::eventloop::{EspEventLoop, EspSubscription, System};
use esp_idf_svc::nvs::{EspNvs, EspNvsPartition, NvsDefault};
use esp_idf_svc::wifi::{BlockingWifi, EspWifi, WifiEvent};
use log::{error, info, warn};

pub(crate) static WIFI_MANAGER: OnceLock<Mutex<WifiManager>> = OnceLock::new();

/// Human-readable mapping for common 802.11 and ESP-IDF Wi-Fi disconnect reason codes.
pub fn wifi_reason_str(reason: u16) -> &'static str {
    match reason {
        1 => "UNSPECIFIED",
        2 => "AUTH_EXPIRE",
        3 => "AUTH_LEAVE",
        4 => "ASSOC_EXPIRE",
        5 => "ASSOC_TOOMANY",
        6 => "NOT_AUTHED",
        7 => "NOT_ASSOCED",
        8 => "ASSOC_LEAVE",
        9 => "ASSOC_NOT_AUTHED",
        10 => "DISASSOC_PWRCAP_BAD",
        11 => "DISASSOC_SUPCHAN_BAD",
        12 => "BSS_TRANSITION",
        13 => "IE_INVALID",
        14 => "MIC_FAILURE",
        15 => "4WAY_HANDSHAKE_TIMEOUT",
        16 => "GROUP_KEY_UPDATE_TIMEOUT",
        17 => "IE_IN_4WAY_DIFFERS",
        18 => "GROUP_CIPHER_INVALID",
        19 => "PAIRWISE_CIPHER_INVALID",
        20 => "AKMP_INVALID",
        21 => "UNSUPP_RSN_IE_VERSION",
        22 => "INVALID_RSN_IE_CAP",
        23 => "802_1X_AUTH_FAILED",
        24 => "CIPHER_SUITE_REJECTED",
        200 => "BEACON_TIMEOUT",
        201 => "NO_AP_FOUND",
        202 => "AUTH_FAIL",
        203 => "ASSOC_FAIL",
        204 => "HANDSHAKE_TIMEOUT",
        205 => "CONNECTION_FAIL",
        206 => "AP_TSF_RESET",
        207 => "ROAMING",
        _ => "UNKNOWN",
    }
}

#[derive(Default)]
pub(crate) struct ConnectionMonitor {
    pub(crate) connected: AtomicBool,
    pub(crate) last_disconnect_reason: Mutex<Option<u16>>,
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub enum WifiState {
    DisconnectedNoCreds,
    DisconnectedWithCreds,
    Connected(String),
}

pub(crate) struct WifiManager {
    pub(crate) ssid: Option<String>,
    pub(crate) password: Option<String>,
    wifi: Option<BlockingWifi<EspWifi<'static>>>,
    pub(crate) sys_loop: EspEventLoop<System>,
    nvs_default: EspNvsPartition<NvsDefault>,
    state: WifiState,
    monitor: Arc<ConnectionMonitor>,
    _subscription: Option<EspSubscription<'static, System>>,
}

impl WifiManager {
    pub fn new(
        nvs_default: EspNvsPartition<NvsDefault>,
        _modem: modem::Modem<'static>,
        sys_loop: EspEventLoop<System>,
    ) -> Self {
        // Drop the modem handle so Modem::steal() can cleanly re-acquire the radio
        drop(_modem);

        let monitor = Arc::new(ConnectionMonitor::default());
        let mon_clone = Arc::clone(&monitor);

        // 1. Subscribe to Wi-Fi system events for asynchronous monitoring & auto-reconnect
        let subscription = sys_loop
            .subscribe::<WifiEvent, _>(move |event| match event {
                WifiEvent::StaConnected(_) => {
                    info!("WiFi event: Station associated and connected to AP");
                    mon_clone.connected.store(true, Ordering::SeqCst);
                    *mon_clone.last_disconnect_reason.lock().unwrap() = None;
                }
                WifiEvent::StaDisconnected(disconn) => {
                    let reason = disconn.reason();
                    warn!(
                        "WiFi event: Station disconnected (reason: {} - {})",
                        reason,
                        wifi_reason_str(reason)
                    );
                    mon_clone.connected.store(false, Ordering::SeqCst);
                    *mon_clone.last_disconnect_reason.lock().unwrap() = Some(reason);

                    // Trigger background auto-reconnection for runtime drops
                    unsafe {
                        let _ = esp_idf_svc::sys::esp_wifi_connect();
                    }
                }
                _ => {}
            })
            .map_err(|e| {
                error!("Failed to subscribe to WifiEvent: {:?}", e);
                e
            })
            .ok();

        let mut manager = WifiManager {
            ssid: None,
            password: None,
            wifi: None,
            sys_loop,
            nvs_default,
            state: WifiState::DisconnectedNoCreds,
            monitor,
            _subscription: subscription,
        };

        manager.load_saved_creds();
        manager
    }

    fn load_saved_creds(&mut self) {
        let nvs: EspNvs<NvsDefault> = EspNvs::new(self.nvs_default.clone(), "storage", true).unwrap();

        let mut buffer: [u8; 256] = [0; 256];
        if let Ok(blob) = nvs.get_blob("ssid", &mut buffer) {
            if let Some(actual_ssid) = blob {
                let mut pass_buffer: [u8; 256] = [0; 256];
                let pass = nvs
                    .get_blob("password", &mut pass_buffer)
                    .expect("NVS to work");
                if let Some(actual_pass) = pass {
                    let ssid = String::from_utf8(actual_ssid.to_vec()).ok().unwrap();
                    let password = String::from_utf8(actual_pass.to_vec()).ok().unwrap();

                    self.ssid = Some(ssid);
                    self.password = Some(password);

                    self.state = WifiState::DisconnectedWithCreds;
                }
            }
        } else {
            info!("No stored wifi credentials. Deferring wifi");
        }
    }

    pub fn process_initial_state(&mut self, suppress_connect: bool) -> anyhow::Result<WifiState> {
        if suppress_connect {
            info!("D2 was held down. Skipping Wi-Fi connect.");
        } else if self.state == WifiState::DisconnectedWithCreds {
            self.robust_connect()?;
        }

        Ok(self.state.clone())
    }

    pub fn robust_connect(&mut self) -> anyhow::Result<()> {
        match &self.state {
            WifiState::Connected(connected_ssid) => {
                if self.ssid.as_ref() == Some(connected_ssid) && self.is_connected() {
                    info!("Already connected to '{}'. Nothing to do.", connected_ssid);
                    return Ok(());
                } else {
                    info!("New SSID creds or connection lost. Unloading WiFi driver.");
                    self.disconnect()?;
                }
            }
            _ => {
                info!("Robust connect called in state {:?}", self.state);
            }
        }

        let ssid = match &self.ssid {
            Some(s) if !s.is_empty() => s.clone(),
            _ => {
                info!("No SSID configured. Skipping connect.");
                return Ok(());
            }
        };
        let password = self.password.clone().unwrap_or_default();

        // Ensure any previously active driver is completely torn down
        if let Some(wifi) = self.wifi.take() {
            drop(wifi);
            std::thread::sleep(Duration::from_millis(200));
        }

        // Fast timeouts per wifi_explanation.md to prevent blocking boot
        const MAX_ATTEMPTS: usize = 3;
        const ASSOC_TIMEOUT: Duration = Duration::from_millis(4000);
        const DHCP_TIMEOUT: Duration = Duration::from_millis(7000);

        for attempt in 1..=MAX_ATTEMPTS {
            info!(
                "Wi-Fi connection attempt {}/{} to '{}'...",
                attempt, MAX_ATTEMPTS, ssid
            );

            self.monitor.connected.store(false, Ordering::SeqCst);
            *self.monitor.last_disconnect_reason.lock().unwrap() = None;

            // 1. Initialize fresh EspWifi driver with Modem::steal()
            let esp_wifi = match EspWifi::new(
                unsafe { modem::Modem::steal() },
                self.sys_loop.clone(),
                Some(self.nvs_default.clone()),
            ) {
                Ok(w) => w,
                Err(e) => {
                    warn!("Failed to initialize EspWifi (attempt {}): {:?}", attempt, e);
                    std::thread::sleep(Duration::from_millis(200));
                    continue;
                }
            };

            let mut wifi = match BlockingWifi::wrap(esp_wifi, self.sys_loop.clone()) {
                Ok(w) => w,
                Err(e) => {
                    warn!("Failed to wrap BlockingWifi (attempt {}): {:?}", attempt, e);
                    std::thread::sleep(Duration::from_millis(200));
                    continue;
                }
            };

            // 2. Set storage mode to RAM to avoid flash wear and write latency across boots
            unsafe {
                esp_wifi_set_storage(wifi_storage_t_WIFI_STORAGE_RAM);
            }

            // 3. Configure station parameters
            let auth_method = if password.is_empty() {
                AuthMethod::None
            } else {
                AuthMethod::WPA2Personal
            };

            let client_config = ClientConfiguration {
                ssid: ssid
                    .as_str()
                    .try_into()
                    .map_err(|_| anyhow::anyhow!("SSID too long"))?,
                password: password
                    .as_str()
                    .try_into()
                    .map_err(|_| anyhow::anyhow!("Password too long"))?,
                auth_method,
                scan_method: ScanMethod::CompleteScan(ScanSortMethod::Signal),
                pmf_cfg: PmfConfiguration::Capable { required: false },
                ..Default::default()
            };

            if let Err(e) = wifi.set_configuration(&Configuration::Client(client_config)) {
                warn!(
                    "Failed to set Wi-Fi configuration (attempt {}): {:?}",
                    attempt, e
                );
                drop(wifi);
                std::thread::sleep(Duration::from_millis(200));
                continue;
            }

            // 4. Start Wi-Fi stack
            if let Err(e) = wifi.start() {
                warn!("Failed to start Wi-Fi stack (attempt {}): {:?}", attempt, e);
                drop(wifi);
                std::thread::sleep(Duration::from_millis(200));
                continue;
            }

            // 5. Country code configuration (reduces TX power to prevent receiver desensitization)
            let country_config = wifi_country_t {
                cc: [b'U', b'S', 0],
                schan: 1,
                nchan: 11,
                max_tx_power: 40, // 10dBm
                policy: esp_idf_svc::sys::wifi_country_policy_t_WIFI_COUNTRY_POLICY_AUTO,
            };
            unsafe {
                let err = esp_wifi_set_country(&country_config);
                if err != esp_idf_svc::sys::ESP_OK {
                    warn!("Failed to set country code: {}", err);
                }
            }

            // 6. Disable modem sleep to keep RF fully energized during association & handshake
            unsafe {
                esp_wifi_set_ps(wifi_ps_type_t_WIFI_PS_NONE);
            }

            // 7. Non-blocking connect initiation
            if let Err(e) = wifi.wifi_mut().connect() {
                warn!(
                    "Non-blocking connect initiation failed (attempt {}): {:?}",
                    attempt, e
                );
                drop(wifi);
                std::thread::sleep(Duration::from_millis(200));
                continue;
            }

            // 8. Phase 1: Fast association detection with aggressive early abort (~50ms)
            let assoc_start = Instant::now();
            let mut associated = false;

            while assoc_start.elapsed() < ASSOC_TIMEOUT {
                if self.monitor.connected.load(Ordering::SeqCst)
                    || wifi.is_connected().unwrap_or(false)
                {
                    associated = true;
                    break;
                }

                // Fast-abort immediately on any disconnect reason (e.g. wrong password, no AP found)
                if let Some(reason) = *self.monitor.last_disconnect_reason.lock().unwrap() {
                    warn!(
                        "Association failed early (attempt {}/{}, reason: {} - {}). Aborting attempt immediately.",
                        attempt, MAX_ATTEMPTS, reason, wifi_reason_str(reason)
                    );
                    break;
                }

                std::thread::sleep(Duration::from_millis(50));
            }

            if !associated {
                // Drop unloads driver, stops stack, and destroys netif cleanly
                drop(wifi);
                std::thread::sleep(Duration::from_millis(200));
                continue;
            }

            info!("Station associated with AP. Waiting for DHCP lease...");

            // 9. Phase 2: DHCP IP lease loop (~100ms ticks)
            let dhcp_start = Instant::now();
            let mut netif_up = false;

            while dhcp_start.elapsed() < DHCP_TIMEOUT {
                if wifi.is_up().unwrap_or(false) {
                    netif_up = true;
                    break;
                }

                if let Some(reason) = *self.monitor.last_disconnect_reason.lock().unwrap() {
                    warn!(
                        "DHCP failed early due to disconnect (reason: {} - {}).",
                        reason,
                        wifi_reason_str(reason)
                    );
                    break;
                }

                std::thread::sleep(Duration::from_millis(100));
            }

            if !netif_up {
                warn!(
                    "DHCP lease acquisition timed out or disconnected (attempt {}/{})",
                    attempt, MAX_ATTEMPTS
                );
                drop(wifi);
                std::thread::sleep(Duration::from_millis(200));
                continue;
            }

            info!("Wi-Fi connected successfully and netif is up!");
            self.wifi = Some(wifi);
            self.state = WifiState::Connected(ssid);
            self.save_credentials();
            return Ok(());
        }

        self.state = WifiState::DisconnectedWithCreds;
        anyhow::bail!("Failed to connect to Wi-Fi after {} attempts", MAX_ATTEMPTS);
    }

    pub fn disconnect(&mut self) -> anyhow::Result<()> {
        if let Some(mut wifi) = self.wifi.take() {
            let _ = wifi.disconnect();
            let _ = wifi.stop();
            drop(wifi);
            std::thread::sleep(Duration::from_millis(200));
        }
        self.monitor.connected.store(false, Ordering::SeqCst);
        if self.ssid.is_some() {
            self.state = WifiState::DisconnectedWithCreds;
        } else {
            self.state = WifiState::DisconnectedNoCreds;
        }
        Ok(())
    }

    pub fn is_connected(&self) -> bool {
        if let Some(wifi) = self.wifi.as_ref() {
            wifi.is_connected().unwrap_or(false) && wifi.is_up().unwrap_or(false)
        } else {
            false
        }
    }

    pub fn save_credentials(&mut self) {
        if let (Some(ssid), Some(password)) = (&self.ssid, &self.password) {
            let nvs: EspNvs<NvsDefault> =
                EspNvs::new(self.nvs_default.clone(), "storage", true).unwrap();
            let _ = nvs.set_blob("ssid", ssid.as_bytes());
            let _ = nvs.set_blob("password", password.as_bytes());
            info!("Saved Wi-Fi credentials to NVS");
        }
    }
}

static CONNECTING: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);

/// Triggers a Wi-Fi connection attempt asynchronously in a background thread
/// so that the main UI event loop is not blocked during long connection/timeout sequences.
pub fn trigger_async_connect() {
    if CONNECTING.swap(true, std::sync::atomic::Ordering::SeqCst) {
        info!("Wi-Fi connection already in progress, skipping redundant trigger");
        return;
    }

    let spawn_res = std::thread::Builder::new()
        .stack_size(8192)
        .name("wifi-connect".to_string())
        .spawn(move || {
            info!("Async Wi-Fi connection thread started");
            if let Some(mgr_mutex) = WIFI_MANAGER.get() {
                if let Ok(mut mgr) = mgr_mutex.lock() {
                    let _ = mgr.robust_connect();
                }
            }
            CONNECTING.store(false, std::sync::atomic::Ordering::SeqCst);
            info!("Async Wi-Fi connection thread completed");
        });

    if let Err(e) = spawn_res {
        CONNECTING.store(false, std::sync::atomic::Ordering::SeqCst);
        log::error!("Failed to spawn async Wi-Fi connection thread: {:?}", e);
    }
}