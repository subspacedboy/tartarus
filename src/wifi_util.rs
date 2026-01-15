use esp_idf_svc::sys::{esp_wifi_deinit, esp_wifi_restore, esp_wifi_stop};
use embedded_svc::wifi::{AuthMethod, ClientConfiguration, Configuration, PmfConfiguration, ScanMethod, ScanSortMethod};
use esp_idf_svc::sys;
use esp_idf_svc::sys::{esp_wifi_set_country, wifi_country_t};
use esp_idf_svc::wifi::{BlockingWifi, EspWifi};

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
