use crate::configuration_generated::club::subjugated::fb::message::configuration::CoordinatorConfiguration;
use crate::internal_contract::verifying_key_serde;
use p256::ecdsa::VerifyingKey;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InternalConfig {
    pub(crate) mqtt_broker_uri: String,
    pub(crate) web_uri: String,
    pub(crate) safety_keys: Option<Vec<InternalSafetyKey>>,
    pub(crate) enable_reset_command: bool,
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
            enable_reset_command: false,
        }
    }
}

impl From<CoordinatorConfiguration<'_>> for InternalConfig {
    fn from(config: CoordinatorConfiguration) -> Self {
        let mut key_holder: Vec<InternalSafetyKey> = Vec::new();
        if let Some(keys) = config.safety_keys() {
            for key in keys {
                let k = InternalSafetyKey {
                    name: key.name().unwrap().parse().unwrap(),
                    public_key: Some(
                        VerifyingKey::from_sec1_bytes(key.public_key().unwrap().bytes())
                            .expect("Valid public key"),
                    ),
                };
                key_holder.push(k);
            }
        }

        let keys = if key_holder.is_empty() {
            None
        } else {
            Some(key_holder)
        };

        InternalConfig {
            web_uri: config.web_uri().unwrap().to_string(),
            mqtt_broker_uri: config.mqtt_uri().unwrap().to_string(),
            safety_keys: keys,
            enable_reset_command: config.enable_reset_command(),
            ..InternalConfig::default()
        }
    }
}
