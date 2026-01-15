use std::cmp::PartialEq;
use std::sync::{Mutex, OnceLock};
use embedded_svc::wifi::{AuthMethod, ClientConfiguration, Configuration, PmfConfiguration, ScanMethod, ScanSortMethod};
use esp_idf_hal::{modem, sys};
use esp_idf_hal::peripheral::Peripheral;
use esp_idf_hal::sys::{esp_wifi_set_country, wifi_country_t};
use esp_idf_svc::eventloop::{EspEventLoop, System};
use esp_idf_svc::nvs::{EspNvs, EspNvsPartition, NvsDefault};
use esp_idf_svc::wifi::{BlockingWifi, EspWifi};
use log::info;

pub(crate) static WIFI_MANAGER: OnceLock<Mutex<WifiManager>> = OnceLock::new();

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
    pub(crate) modem:  modem::Modem,
    nvs_default: EspNvsPartition<NvsDefault>,
    state: WifiState,
}

impl WifiManager {
    pub fn new(nvs_default: EspNvsPartition<NvsDefault>,
               modem:  modem::Modem,
               sys_loop: EspEventLoop<System>,
    ) -> Self {
        let mut manager = WifiManager { ssid: None,
            password: None,
            wifi: None,
            sys_loop,
            modem,
            nvs_default,
            state: WifiState::DisconnectedNoCreds
        };

        manager.load_saved_creds(false);
        manager
    }

    fn load_saved_creds(&mut self, suppress_connect : bool) {
        let nvs: EspNvs<NvsDefault> = EspNvs::new(self.nvs_default.clone(), "storage", true).unwrap();

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

                    self.ssid = Some(ssid);
                    self.password = Some(password);

                    self.state = WifiState::DisconnectedWithCreds;
                }
            }
        } else {
            info!("No stored wifi credentials. Deferring wifi");
        }
    }

    pub fn process_initial_state(&mut self, suppress_connect : bool) -> anyhow::Result<WifiState> {
        if suppress_connect {
            info!("D2 was held down. Skipping Wi-Fi connect.");
        } else {
            if self.state == WifiState::DisconnectedWithCreds {
                self.robust_connect()?;
            }
        }

        Ok(self.state.clone())
    }

    unsafe fn load_wifi_driver(&mut self) {
        let mut wifi: BlockingWifi<EspWifi> = BlockingWifi::wrap(
            EspWifi::new(self.modem.clone_unchecked(), self.sys_loop.clone(), Some(self.nvs_default.clone()))
                .expect("EspWifi to work"),
            self.sys_loop.clone(),
        ).expect("Wifi to start");

        wifi.start().expect("Wifi should have started");
        unsafe {
            sys::esp_wifi_set_max_tx_power(40);
        }

        self.wifi = Some(wifi);
    }

    fn deload_wifi_driver(&mut self) {
        info!("deloading driver");
        let wifi = self.wifi.take().unwrap();
        drop(wifi);
        std::thread::sleep(std::time::Duration::from_millis(250));
    }

    pub fn robust_connect(&mut self) -> anyhow::Result<()> {
        match &self.state {
            WifiState::Connected(connected_ssid) => {
                if self.ssid.as_ref().unwrap() == connected_ssid {
                    info!("Already connected to the right SSID. Nothing to do.");
                    // Already connected to the right SSID. Nothing to do.
                    return Ok(());
                } else {
                    info!("New SSID creds. Unloading+reloading WiFi driver.");
                    // Robust connect but we were already connected to a different SSID.
                    self.disconnect()?;
                    self.deload_wifi_driver();
                }
            }
            _ => {
                info!("Robust connect called in state {:?}", self.state);
            }
        }

        info!("new_connect");
        unsafe {
            self.load_wifi_driver();
        }

        if let Ok(worked) = self.connect_wifi() {
            self.save_credentials();
        } else {
            // Something errored out. Try to recover but completely deinitialize driver
            self.deload_wifi_driver();
            unsafe {
                self.load_wifi_driver();
            }
            match self.connect_wifi() {
                Ok(_) => {
                    info!("Robust connect succeeded after driver re-init.");
                    self.save_credentials();
                }
                Err(e) => {
                    info!("Robust connect failed after driver re-init. Error: {:?}", e);
                    self.deload_wifi_driver();
                    return Err(e);
                }
            }
        }

        Ok(())
    }

    fn disconnect(&mut self) -> anyhow::Result<()> {
        let wifi = self.wifi.as_mut().unwrap();
        wifi.disconnect()?;
        wifi.stop()?;
        self.state = WifiState::DisconnectedWithCreds;
        Ok(())
    }

    fn connect_wifi(
        &mut self
    ) -> anyhow::Result<()> {
        let ssid = self.ssid.clone().unwrap();
        let password = self.password.clone().unwrap();

        let wifi_configuration: Configuration = Configuration::Client(ClientConfiguration {
            ssid: ssid.as_str().parse().expect("Invalid SSID"),
            password: password.as_str().parse().expect("Invalid Password"),
            auth_method: AuthMethod::WPA2Personal,
            scan_method: ScanMethod::CompleteScan(ScanSortMethod::Signal),
            pmf_cfg: PmfConfiguration::Capable { required: false },
            ..Default::default()
        });

        let wifi = self.wifi.as_mut().unwrap();

        // 2. SET CONFIG & START
        wifi.set_configuration(&wifi_configuration)?;
        wifi.start()?;

        // 3. SET COUNTRY (Must be after start)
        let country_config = wifi_country_t {
            cc: [b'U', b'S', 0],
            schan: 1,
            nchan: 11,
            max_tx_power: 40, // Reduced to 10dBm to prevent "shouting" at the router
            policy: esp_idf_svc::sys::wifi_country_policy_t_WIFI_COUNTRY_POLICY_AUTO,
        };
        unsafe {
            let err = esp_wifi_set_country(&country_config);
            if err != esp_idf_svc::sys::ESP_OK {
                log::error!("Failed to set country code: {}", err);
            }
        }

        std::thread::sleep(std::time::Duration::from_millis(250));

        info!("Connecting to WiFi...");

        wifi.connect()?;
        info!("Wifi connected");
        wifi.wait_netif_up()?;
        info!("Wifi netif up");

        self.state = WifiState::Connected(ssid);
        Ok(())
    }

    pub fn is_connected(&self) -> bool {
        info!("WifiManager -> is_connected?");

        if self.wifi.is_none() {
            return false;
        }

        let wifi = self.wifi.as_ref().unwrap();
        wifi.is_connected().unwrap() && wifi.is_up().unwrap()
    }

    pub fn save_credentials(&mut self) {
        let mut nvs: EspNvs<NvsDefault> = EspNvs::new(self.nvs_default.clone(), "storage", true).unwrap();

        let ssid = self.ssid.as_ref().unwrap().as_bytes();
        let password = self.password.as_ref().unwrap().as_bytes();
        nvs.set_blob("ssid", ssid).unwrap();
        nvs.set_blob("password", password).unwrap();
    }
}