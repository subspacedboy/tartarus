use crate::contract_generated::club::subjugated::fb::message::{
    AbortCommand, Bot, Contract, LockCommand, Permission, ReleaseCommand, ResetCommand,
    UnlockCommand,
};
use p256::ecdsa::VerifyingKey;
use serde::{Deserialize, Serialize};

fn default_true() -> bool {
    true
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SaveState {
    pub(crate) internal_contract: InternalContract,
    pub(crate) is_locked: bool,
    #[serde(default)]
    pub(crate) emergency_unlocked: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InternalContract {
    pub serial_number: u16,
    pub temporary_unlock_allowed: bool,
    #[serde(default = "default_true")]
    pub emergency_unlock_allowed: bool,
    #[serde(with = "verifying_key_serde")]
    pub public_key: Option<VerifyingKey>,
    #[serde(default, skip_serializing)]
    #[allow(dead_code)]
    pub original_bytes: Vec<u8>,
    pub command_counter: u16,
    pub bots: Vec<InternalBot>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InternalBot {
    pub(crate) name: String,
    #[serde(with = "verifying_key_serde")]
    pub public_key: Option<VerifyingKey>,
    pub permission: InternalPermission,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InternalPermission {
    pub receive_events: bool,
    pub can_unlock: bool,
    pub can_lock: bool,
    pub can_release: bool,
}

#[derive(Debug, Clone)]
#[allow(dead_code)]
pub struct InternalUnlockCommand {
    pub contract_serial_number: u16,
    pub serial_number: u16,
    pub counter: u16,
    pub authority: Option<String>,
}

impl InternalUnlockCommand {
    pub fn with_authority(mut self, authority: Option<String>) -> Self {
        self.authority = authority;
        self
    }
}

#[derive(Debug, Clone)]
#[allow(dead_code)]
pub struct InternalLockCommand {
    pub contract_serial_number: u16,
    pub serial_number: u16,
    pub counter: u16,
    pub authority: Option<String>,
}

impl InternalLockCommand {
    pub fn with_authority(mut self, authority: Option<String>) -> Self {
        self.authority = authority;
        self
    }
}

#[derive(Debug, Clone)]
#[allow(dead_code)]
pub struct InternalReleaseCommand {
    pub contract_serial_number: u16,
    pub serial_number: u16,
    pub counter: u16,
    pub authority: Option<String>,
}

impl InternalReleaseCommand {
    pub fn with_authority(mut self, authority: Option<String>) -> Self {
        self.authority = authority;
        self
    }
}

#[derive(Debug, Clone)]
#[allow(dead_code)]
pub struct InternalAbortCommand {
    pub contract_serial_number: u16,
    pub serial_number: u16,
    pub counter: u16,
    pub authority: Option<String>,
}

impl InternalAbortCommand {
    pub fn with_authority(mut self, authority: Option<String>) -> Self {
        self.authority = authority;
        self
    }
}

#[derive(Debug, Clone)]
pub struct InternalResetCommand {
    session: String,
    serial_number: u16,
    pub authority: Option<String>,
}

impl InternalResetCommand {
    pub fn session(&self) -> &str {
        self.session.as_str()
    }
    pub fn serial_number(&self) -> u16 {
        self.serial_number
    }
    pub fn with_authority(mut self, authority: Option<String>) -> Self {
        self.authority = authority;
        self
    }
}

impl From<Contract<'_>> for InternalContract {
    fn from(contract: Contract<'_>) -> InternalContract {
        let verifying_key = crate::internal_config::parse_verifying_key(contract.public_key().unwrap().bytes())
            .expect("Valid public key");

        let mut internal_bots: Vec<InternalBot> = Vec::new();

        if let Some(bots) = contract.bots() {
            for bot in bots.iter() {
                let internal_bot: InternalBot = bot.into();
                internal_bots.push(internal_bot);
            }
        } else {
            println!("No bots found in the contract.");
        }

        Self {
            serial_number: contract.serial_number(),
            temporary_unlock_allowed: contract.is_temporary_unlock_allowed(),
            emergency_unlock_allowed: contract.is_emergency_unlock_allowed(),
            public_key: Some(verifying_key),
            original_bytes: Vec::new(),
            command_counter: 0,
            bots: internal_bots,
        }
    }
}

impl From<UnlockCommand<'_>> for InternalUnlockCommand {
    fn from(unlock_command: UnlockCommand) -> InternalUnlockCommand {
        Self {
            contract_serial_number: unlock_command.contract_serial_number(),
            serial_number: unlock_command.serial_number(),
            counter: unlock_command.counter(),
            authority: None,
        }
    }
}

impl From<LockCommand<'_>> for InternalLockCommand {
    fn from(lock_command: LockCommand) -> InternalLockCommand {
        Self {
            contract_serial_number: lock_command.contract_serial_number(),
            serial_number: lock_command.serial_number(),
            counter: lock_command.counter(),
            authority: None,
        }
    }
}

impl From<ReleaseCommand<'_>> for InternalReleaseCommand {
    fn from(release_command: ReleaseCommand) -> InternalReleaseCommand {
        Self {
            contract_serial_number: release_command.contract_serial_number(),
            serial_number: release_command.serial_number(),
            counter: release_command.counter(),
            authority: None,
        }
    }
}

impl From<AbortCommand<'_>> for InternalAbortCommand {
    fn from(abort_command: AbortCommand) -> InternalAbortCommand {
        Self {
            contract_serial_number: abort_command.contract_serial_number(),
            serial_number: abort_command.serial_number(),
            counter: abort_command.counter(),
            authority: None,
        }
    }
}

impl From<Bot<'_>> for InternalBot {
    fn from(bot: Bot) -> InternalBot {
        let internal_permission: Option<InternalPermission> =
            bot.permissions().as_ref().map(|p| (*p).into());

        let verifying_key = crate::internal_config::parse_verifying_key(bot.public_key().unwrap().bytes())
            .expect("Valid public key");

        Self {
            name: bot
                .name()
                .expect("Bot should always have a name")
                .to_string(),
            public_key: Some(verifying_key),
            permission: internal_permission.unwrap(),
        }
    }
}

impl From<Permission<'_>> for InternalPermission {
    fn from(permission: Permission) -> InternalPermission {
        Self {
            receive_events: permission.receive_events(),
            can_unlock: permission.can_unlock(),
            can_lock: permission.can_lock(),
            can_release: permission.can_release(),
        }
    }
}

impl From<ResetCommand<'_>> for InternalResetCommand {
    fn from(reset: ResetCommand) -> InternalResetCommand {
        Self {
            session: reset.session().unwrap().to_string(),
            serial_number: reset.serial_number(),
            authority: None,
        }
    }
}

pub mod verifying_key_serde {
    use p256::ecdsa::VerifyingKey;
    use serde::de::Error;
    use serde::{Deserialize, Deserializer, Serializer};

    pub fn serialize<S>(key: &Option<VerifyingKey>, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        match key {
            Some(k) => {
                let bytes = k.to_encoded_point(true);
                serializer.serialize_some(bytes.as_bytes())
            }
            None => serializer.serialize_none(),
        }
    }

    pub fn deserialize<'de, D>(deserializer: D) -> Result<Option<VerifyingKey>, D::Error>
    where
        D: Deserializer<'de>,
    {
        let maybe_bytes: Option<Vec<u8>> = Option::deserialize(deserializer)?;
        match maybe_bytes {
            Some(bytes) => {
                let key = VerifyingKey::from_sec1_bytes(&bytes).map_err(D::Error::custom)?;
                Ok(Some(key))
            }
            None => Ok(None),
        }
    }
}

#[cfg(test)]
#[allow(unused_imports)]
mod tests {
    use super::*;

    #[test]
    fn test_internal_contract_serde_backward_compatibility() {
        // Simulating JSON without emergency_unlock_allowed field (legacy saved state)
        let legacy_json = r#"{
            "serial_number": 42,
            "temporary_unlock_allowed": false,
            "public_key": null,
            "command_counter": 1,
            "bots": []
        }"#;

        let parsed: InternalContract = serde_json::from_str(legacy_json).unwrap();
        assert_eq!(parsed.serial_number, 42);
        assert_eq!(parsed.temporary_unlock_allowed, false);
        // Should default to true!
        assert_eq!(parsed.emergency_unlock_allowed, true);
    }

    #[test]
    fn test_save_state_serde_backward_compatibility() {
        // Simulating legacy SaveState JSON without emergency_unlocked field
        let legacy_save_state_json = r#"{
            "internal_contract": {
                "serial_number": 100,
                "temporary_unlock_allowed": true,
                "public_key": null,
                "command_counter": 5,
                "bots": []
            },
            "is_locked": true
        }"#;

        let state: SaveState = serde_json::from_str(legacy_save_state_json).unwrap();
        assert_eq!(state.is_locked, true);
        assert_eq!(state.internal_contract.emergency_unlock_allowed, true);
        // emergency_unlocked should default to false!
        assert_eq!(state.emergency_unlocked, false);
    }

    #[test]
    fn test_save_state_serde_roundtrip_with_emergency_unlocked() {
        let save_state = SaveState {
            internal_contract: InternalContract {
                serial_number: 101,
                temporary_unlock_allowed: false,
                emergency_unlock_allowed: true,
                public_key: None,
                original_bytes: Vec::new(),
                command_counter: 0,
                bots: Vec::new(),
            },
            is_locked: false,
            emergency_unlocked: true,
        };

        let json = serde_json::to_string(&save_state).unwrap();
        let restored: SaveState = serde_json::from_str(&json).unwrap();
        assert_eq!(restored.is_locked, false);
        assert_eq!(restored.emergency_unlocked, true);
        assert_eq!(restored.internal_contract.emergency_unlock_allowed, true);
    }
}
