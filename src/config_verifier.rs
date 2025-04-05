use crate::internal_config::InternalConfig;

pub struct ConfigVerifier {}

impl ConfigVerifier {
    pub fn read_configuration(incoming_data: Vec<u8>) -> Result<InternalConfig, String> {
        match crate::configuration_generated::club::subjugated::fb::message::configuration::root_as_coordinator_configuration(incoming_data.as_slice()) {
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
