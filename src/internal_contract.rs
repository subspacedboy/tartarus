use p256::ecdsa::VerifyingKey;
use crate::contract_generated::subjugated;
use crate::contract_generated::subjugated::club::EndCondition;

#[derive(Debug)]
pub struct InternalContract {
    pub temporary_unlock_allowed : bool,
    pub unremovable: bool,
    pub end_criteria: EndCriteria,
    pub temp_unlock_rules: Vec<TempUnlockRules>,
    pub public_key: VerifyingKey
}

#[derive(Debug)]
pub struct InternalPartialContract {
    pub full_address: String
}

#[derive(Debug)]
pub enum EndCriteria {
    WhenISaySo,
    Time
}

#[derive(Debug)]
pub enum TempUnlockRules {
    Remaining(u16),
    TimeLimit(u16)
}

impl From<subjugated::club::Contract<'_>> for InternalContract {
    fn from(contract: subjugated::club::Contract<'_>) -> InternalContract {
        let end_condition = match contract.end_condition_type() {
            EndCondition::WhenISaySo => EndCriteria::WhenISaySo,
            EndCondition::TimeEndCondition => EndCriteria::Time,
            _ => EndCriteria::WhenISaySo,
        };

        let mut ic = Self {
            temp_unlock_rules: Vec::new(),
            unremovable: contract.is_unremovable(),
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

impl From<subjugated::club::PartialContract<'_>> for InternalPartialContract {
    fn from(contract: subjugated::club::PartialContract<'_>) -> InternalPartialContract {
        Self {
            full_address: contract.complete_contract_address().unwrap().to_string()
        }
    }
}