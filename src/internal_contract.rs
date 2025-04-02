use p256::ecdsa::VerifyingKey;
use crate::contract_generated::club::subjugated::fb::message::{Contract, EndCondition, LockCommand, UnlockCommand};

#[derive(Debug, Clone)]
pub struct InternalContract {
    pub serial_number: u16,
    pub temporary_unlock_allowed : bool,
    pub unremovable: bool,
    pub end_criteria: EndCriteria,
    pub temp_unlock_rules: Vec<TempUnlockRules>,
    pub public_key: VerifyingKey
}

#[derive(Debug, Clone)]
pub struct InternalUnlockCommand {
    pub contract_serial_number: u16,
    pub serial_number: u16,
    pub counter: u16
}

#[derive(Debug, Clone)]
pub struct InternalLockCommand {
    pub contract_serial_number: u16,
    pub serial_number: u16,
    pub counter: u16
}

#[derive(Debug, Clone)]
pub struct InternalUnverified {
    pub serial_number: u16,
    pub counter: u16
}

#[derive(Debug, Clone)]
pub enum EndCriteria {
    WhenISaySo,
    Time
}

#[derive(Debug, Clone)]
pub enum TempUnlockRules {
    Remaining(u16),
    TimeLimit(u16)
}

impl From<Contract<'_>> for InternalContract {
    fn from(contract: Contract<'_>) -> InternalContract {
        let end_condition = match contract.end_condition_type() {
            EndCondition::WhenISaySo => EndCriteria::WhenISaySo,
            EndCondition::TimeEndCondition => EndCriteria::Time,
            _ => EndCriteria::WhenISaySo,
        };

        let mut ic = Self {
            serial_number: contract.serial_number(),
            temp_unlock_rules: Vec::new(),
            unremovable: false,
            temporary_unlock_allowed: contract.is_temporary_unlock_allowed(),
            end_criteria: end_condition,
            public_key: VerifyingKey::from_sec1_bytes(contract.public_key().unwrap().bytes()).expect("Valid public key")
        };

        if let Some(rules) = contract.unlock_rules() {
            if rules.max_unlocks() > 0 {
                ic.temp_unlock_rules.push(TempUnlockRules::Remaining(rules.max_unlocks()))
            }
            if rules.time_limit() > 0 {
                ic.temp_unlock_rules.push(TempUnlockRules::TimeLimit(rules.time_limit()));
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
