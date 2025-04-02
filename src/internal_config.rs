use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InternalConfig {
    pub(crate) mqtt_broker_uri: String,
    pub(crate) web_uri: String
}

impl Default for InternalConfig {
    fn default() -> Self {
        Self{
            mqtt_broker_uri: "wss://tartarus-mqtt.subjugated.club:8080".to_string(),
            web_uri: "https://tartarus.subjugated.club".to_string()
        }
    }
}