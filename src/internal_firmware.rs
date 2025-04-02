use crate::internal_contract::{
    InternalContract, InternalLockCommand, InternalReleaseCommand, InternalUnlockCommand,
};
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

#[derive(Debug, Clone)]
pub struct InternalChallenge {
    pub nonce: Vec<u8>,
    pub request_id: i64,
}

impl InternalChallenge {
    pub fn new(nonce: Vec<u8>, request_id: i64) -> Self {
        Self { nonce, request_id }
    }
}

#[derive(Debug, Clone)]
pub enum FirmwareMessageType {
    Challenge(InternalChallenge),
    FirmwareResponse,
}
