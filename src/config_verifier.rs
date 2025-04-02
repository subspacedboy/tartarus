use flatbuffers::InvalidFlatbuffer;
use p256::ecdsa::VerifyingKey;
use crate::configuration_generated::club::subjugated::fb::message::configuration::CoordinatorConfiguration;
use crate::internal_config::{InternalConfig, InternalSafetyKey};

pub struct ConfigVerifier {}

impl ConfigVerifier {
    pub fn new() -> Self {
        ConfigVerifier {}
    }

    pub fn read_configuration(incoming_data: Vec<u8>) -> Result<InternalConfig, String> {
        match crate::configuration_generated::club::subjugated::fb::message::configuration::root_as_coordinator_configuration(incoming_data.as_slice()) {
            Ok(config_data) => {
                log::info!("Configuration data: {:?}", config_data);

                let mut key_holder : Vec<InternalSafetyKey> = Vec::new();
                if let Some(keys) = config_data.safety_keys() {
                    for key in keys {
                        let k = InternalSafetyKey {
                            name: key.name().unwrap().parse().unwrap(),
                            public_key: Some(VerifyingKey::from_sec1_bytes(key.public_key().unwrap().bytes()).expect("Valid public key"))
                        };
                        key_holder.push(k);
                    }
                }

                let keys = if key_holder.is_empty() {
                    None
                } else {
                    Some(key_holder)
                };

                let result = InternalConfig {
                    web_uri: config_data.web_uri().unwrap().to_string(),
                    mqtt_broker_uri: config_data.mqtt_uri().unwrap().to_string(),
                    safety_keys: keys
                };

                Ok(result)
            }
            Err(_) => {
                Err(String::from("Not configuration data"))
            }
        }
    }
}