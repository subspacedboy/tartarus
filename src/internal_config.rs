use crate::internal_contract::verifying_key_serde;
use p256::ecdsa::VerifyingKey;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InternalConfig {
    pub(crate) mqtt_broker_uri: String,
    pub(crate) web_uri: String,
    pub(crate) safety_keys: Option<Vec<InternalSafetyKey>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InternalSafetyKey {
    pub(crate) name: String,
    #[serde(with = "verifying_key_serde")]
    pub(crate) public_key: Option<VerifyingKey>,
}

impl Default for InternalConfig {
    fn default() -> Self {
        Self {
            mqtt_broker_uri: "wss://tartarus-mqtt.subjugated.club:4447".to_string(),
            web_uri: "https://tartarus.subjugated.club".to_string(),
            safety_keys: None,
        }
    }
}
