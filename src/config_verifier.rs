use flatbuffers::InvalidFlatbuffer;
use crate::configuration_generated::club::subjugated::fb::message::configuration::CoordinatorConfiguration;
use crate::internal_config::InternalConfig;

pub struct ConfigVerifier {}

impl ConfigVerifier {
    pub fn new() -> Self {
        ConfigVerifier {}
    }

    pub fn read_configuration(incoming_data: Vec<u8>) -> Result<InternalConfig, String> {
        match crate::configuration_generated::club::subjugated::fb::message::configuration::root_as_coordinator_configuration(incoming_data.as_slice()) {
            Ok(config_data) => {
                log::info!("Configuration data: {:?}", config_data);

                let result = InternalConfig {
                    web_uri: config_data.web_uri().unwrap().to_string(),
                    mqtt_broker_uri: config_data.mqtt_uri().unwrap().to_string(),
                };

                Ok(result)
            }
            Err(_) => {
                Err(String::from("Not configuration data"))
            }
        }
    }
}