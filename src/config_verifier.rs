use crate::internal_config::InternalConfig;
use data_encoding::BASE64;

pub struct ConfigVerifier {}

impl ConfigVerifier {
    pub fn read_configuration(incoming_data: Vec<u8>) -> Result<InternalConfig, String> {
        let buffer = BASE64.decode(incoming_data.as_slice()).unwrap_or(incoming_data);
        match crate::configuration_generated::club::subjugated::fb::message::configuration::root_as_coordinator_configuration(buffer.as_slice()) {
            Ok(config_data) => {
                log::info!("Configuration data: {:?}", config_data);
                Ok(config_data.into())
            }
            Err(_) => {
                Err(String::from("Not configuration data"))
            }
        }
    }
}
