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
    #[serde(with = "verifying_key_serde")]
    login_token_public_key: Option<VerifyingKey>,
}

impl InternalConfig {
    pub(crate) fn login_token_public_key(&self) -> &VerifyingKey {
        self.login_token_public_key.as_ref().unwrap()
    }

    pub(crate) fn has_token_public_key(&self) -> bool {
        self.login_token_public_key.is_some()
    }
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
            mqtt_broker_uri: "wss://tartarus-mqtt.subjugated.club:4447/mqtt".to_string(),
            web_uri: "https://tartarus.subjugated.club".to_string(),
            safety_keys: None,
            enable_reset_command: false,
            login_token_public_key: None,
        }
    }
}

pub fn parse_verifying_key(bytes: &[u8]) -> Result<VerifyingKey, p256::ecdsa::Error> {
    // 1. Try raw SEC1 format (compressed 33-byte or uncompressed 65-byte)
    if let Ok(key) = VerifyingKey::from_sec1_bytes(bytes) {
        return Ok(key);
    }

    // 2. Try PKCS#8 / SPKI DER format
    use p256::pkcs8::DecodePublicKey;
    if let Ok(key) = VerifyingKey::from_public_key_der(bytes) {
        return Ok(key);
    }

    // 3. Fallback: if it's DER SubjectPublicKeyInfo with a 65-byte uncompressed key at the end
    if bytes.len() >= 65 && bytes[bytes.len() - 65] == 0x04 {
        if let Ok(key) = VerifyingKey::from_sec1_bytes(&bytes[bytes.len() - 65..]) {
            return Ok(key);
        }
    }

    // Return the original error
    VerifyingKey::from_sec1_bytes(bytes)
}

impl From<CoordinatorConfiguration<'_>> for InternalConfig {
    fn from(config: CoordinatorConfiguration) -> Self {
        let mut key_holder: Vec<InternalSafetyKey> = Vec::new();
        if let Some(keys) = config.safety_keys() {
            for key in keys {
                let name = match key.name() {
                    Some(n) => match n.parse() {
                        Ok(parsed) => parsed,
                        Err(e) => {
                            log::error!("Failed to parse safety key name: {:?}", e);
                            continue;
                        }
                    },
                    None => continue,
                };

                let pub_key_bytes = match key.public_key() {
                    Some(pk) => pk.bytes(),
                    None => continue,
                };

                match parse_verifying_key(pub_key_bytes) {
                    Ok(parsed_key) => {
                        key_holder.push(InternalSafetyKey {
                            name,
                            public_key: Some(parsed_key),
                        });
                    }
                    Err(e) => {
                        log::error!("Failed to parse safety key '{}' public key: {:?}", name, e);
                    }
                }
            }
        }

        let keys = if key_holder.is_empty() {
            None
        } else {
            Some(key_holder)
        };

        let login_token_public_key: Option<VerifyingKey> =
            if let Some(login_token_key) = config.login_token_public_key() {
                match parse_verifying_key(login_token_key.bytes()) {
                    Ok(k) => {
                        log::info!("Created the login_token_public_key");
                        Some(k)
                    }
                    Err(e) => {
                        log::error!("Failed to parse login_token_public_key: {:?}", e);
                        None
                    }
                }
            } else {
                None
            };

        InternalConfig {
            web_uri: config.web_uri().unwrap_or("").to_string(),
            mqtt_broker_uri: config.mqtt_uri().unwrap_or("").to_string(),
            safety_keys: keys,
            enable_reset_command: config.enable_reset_command(),
            login_token_public_key,
            ..InternalConfig::default()
        }
    }
}
