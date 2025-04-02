use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InternalFirmware {
    pub major: u16,
    pub minor: u16,
    pub build: u16,
    signature: Vec<u8>,
}

impl InternalFirmware {}

impl Default for InternalFirmware {
    fn default() -> Self {
        Self {
            major: 0,
            minor: 0,
            build: 0,
            signature: Vec::new(),
        }
    }
}