use crate::contract_generated::club::subjugated::fb::message::{
    Contract, EndCondition, LockCommand, ReleaseCommand, UnlockCommand,
};
use p256::ecdsa::VerifyingKey;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SaveState {
    pub(crate) internal_contract: InternalContract,
    pub(crate) is_locked: bool,
    // pub(crate) verifying_key_bytes: Vec<u8>
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InternalContract {
    pub serial_number: u16,
    pub temporary_unlock_allowed: bool,
    pub unremovable: bool,
    pub end_criteria: EndCriteria,
    pub temp_unlock_rules: Vec<TempUnlockRules>,
    #[serde(with = "verifying_key_serde")]
    pub public_key: Option<VerifyingKey>,
    pub original_bytes: Vec<u8>,
    pub command_counter: u16,
}

#[derive(Debug, Clone)]
pub struct InternalUnlockCommand {
    pub contract_serial_number: u16,
    pub serial_number: u16,
    pub counter: u16,
}

#[derive(Debug, Clone)]
pub struct InternalLockCommand {
    pub contract_serial_number: u16,
    pub serial_number: u16,
    pub counter: u16,
}

#[derive(Debug, Clone)]
pub struct InternalReleaseCommand {
    pub contract_serial_number: u16,
    pub serial_number: u16,
    pub counter: u16,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum EndCriteria {
    WhenISaySo,
    Time,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum TempUnlockRules {
    Remaining(u16),
    TimeLimit(u16),
}

// impl InternalContract {
//     pub fn get_verifying_key(&self) -> VerifyingKey {
//
//         VerifyingKey::from_sec1_bytes(contract.public_key().unwrap().bytes()).expect("Valid public key"),
//     }
// }

impl From<Contract<'_>> for InternalContract {
    fn from(contract: Contract<'_>) -> InternalContract {
        let end_condition = match contract.end_condition_type() {
            EndCondition::WhenISaySo => EndCriteria::WhenISaySo,
            EndCondition::TimeEndCondition => EndCriteria::Time,
            _ => EndCriteria::WhenISaySo,
        };

        let original_bytes = contract._tab.buf().to_vec();
        let verifying_key = VerifyingKey::from_sec1_bytes(contract.public_key().unwrap().bytes())
            .expect("Valid public key");

        let mut ic = Self {
            serial_number: contract.serial_number(),
            temp_unlock_rules: Vec::new(),
            unremovable: false,
            temporary_unlock_allowed: contract.is_temporary_unlock_allowed(),
            end_criteria: end_condition,
            public_key: Some(verifying_key),
            // public_key: VerifyingKey::from_sec1_bytes(contract.public_key().unwrap().bytes()).expect("Valid public key"),
            original_bytes,
            command_counter: 0,
        };

        if let Some(rules) = contract.unlock_rules() {
            if rules.max_unlocks() > 0 {
                ic.temp_unlock_rules
                    .push(TempUnlockRules::Remaining(rules.max_unlocks()))
            }
            if rules.time_limit() > 0 {
                ic.temp_unlock_rules
                    .push(TempUnlockRules::TimeLimit(rules.time_limit()));
            }
        }

        ic
    }
}

impl From<UnlockCommand<'_>> for InternalUnlockCommand {
    fn from(unlock_command: UnlockCommand) -> InternalUnlockCommand {
        Self {
            contract_serial_number: unlock_command.contract_serial_number(),
            serial_number: unlock_command.serial_number(),
            counter: unlock_command.counter(),
        }
    }
}

impl From<LockCommand<'_>> for InternalLockCommand {
    fn from(lock_command: LockCommand) -> InternalLockCommand {
        Self {
            contract_serial_number: lock_command.contract_serial_number(),
            serial_number: lock_command.serial_number(),
            counter: lock_command.counter(),
        }
    }
}

impl From<ReleaseCommand<'_>> for InternalReleaseCommand {
    fn from(release_command: ReleaseCommand) -> InternalReleaseCommand {
        Self {
            contract_serial_number: release_command.contract_serial_number(),
            serial_number: release_command.serial_number(),
            counter: release_command.counter(),
        }
    }
}

pub mod verifying_key_serde {
    use p256::ecdsa::VerifyingKey;
    use p256::EncodedPoint;
    use serde::de::Error;
    use serde::{Deserialize, Deserializer, Serialize, Serializer};

    pub fn serialize<S>(key: &Option<VerifyingKey>, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        if let Some(key) = key {
            let bytes = key.to_encoded_point(true).as_bytes().to_vec(); // Serialize as compressed SEC1
            Ok(bytes.serialize(serializer)?)
        } else {
            serializer.serialize_none()
        }
    }

    pub fn deserialize<'de, D>(deserializer: D) -> Result<Option<VerifyingKey>, D::Error>
    where
        D: Deserializer<'de>,
    {
        let bytes: Vec<u8> = Deserialize::deserialize(deserializer)?;
        let encoded_point = EncodedPoint::from_bytes(&bytes).map_err(D::Error::custom)?;
        let vk = VerifyingKey::from_encoded_point(&encoded_point).map_err(D::Error::custom)?;
        Ok(Some(vk))
    }
}
