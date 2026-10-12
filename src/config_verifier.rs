use crate::internal_config::InternalConfig;
use data_encoding::BASE64;

pub struct ConfigVerifier {}

impl ConfigVerifier {
    pub fn read_configuration(incoming_data: &[u8]) -> Result<InternalConfig, String> {
        let decoded = BASE64.decode(incoming_data).ok();
        let buffer_slice = decoded.as_deref().unwrap_or(incoming_data);
        match crate::configuration_generated::club::subjugated::fb::message::configuration::root_as_coordinator_configuration(buffer_slice) {
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
