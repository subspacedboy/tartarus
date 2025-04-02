use embedded_svc::wifi::{AuthMethod, ClientConfiguration, Configuration};
use esp_idf_svc::wifi::{BlockingWifi, EspWifi};

pub fn connect_wifi(wifi: &mut BlockingWifi<EspWifi<'static>>, ssid: &String, password: &String) -> anyhow::Result<()> {
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

pub fn parse_wifi_qr(qr_code: String) -> Option<(String, String)> {
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