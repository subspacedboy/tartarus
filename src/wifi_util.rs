pub fn parse_wifi_qr(qr_code: &str) -> Option<(String, String)> {
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
