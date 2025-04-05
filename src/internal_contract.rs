use crate::contract_generated::club::subjugated::fb::message::{
    AbortCommand, Bot, Contract, LockCommand, Permission, ReleaseCommand, ResetCommand,
    UnlockCommand,
};
use p256::ecdsa::VerifyingKey;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SaveState {
    pub(crate) internal_contract: InternalContract,
    pub(crate) is_locked: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InternalContract {
    pub serial_number: u16,
    pub temporary_unlock_allowed: bool,
    #[serde(with = "verifying_key_serde")]
    pub public_key: Option<VerifyingKey>,
    pub original_bytes: Vec<u8>,
    pub command_counter: u16,
    pub bots: Vec<InternalBot>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InternalBot {
    pub(crate) name: String,
    #[serde(with = "verifying_key_serde")]
    pub public_key: Option<VerifyingKey>,
    permission: InternalPermission,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InternalPermission {
    receive_events: bool,
    can_unlock: bool,
    can_release: bool,
}

#[derive(Debug, Clone)]
pub struct InternalUnlockCommand {
    contract_serial_number: u16,
    pub serial_number: u16,
    pub counter: u16,
}

#[derive(Debug, Clone)]
pub struct InternalLockCommand {
    contract_serial_number: u16,
    pub serial_number: u16,
    pub counter: u16,
}

#[derive(Debug, Clone)]
pub struct InternalReleaseCommand {
    contract_serial_number: u16,
    pub serial_number: u16,
    pub counter: u16,
}

#[derive(Debug, Clone)]
pub struct InternalAbortCommand {
    contract_serial_number: u16,
    pub serial_number: u16,
    pub counter: u16,
}

#[derive(Debug, Clone)]
pub struct InternalResetCommand {
    session: String,
    serial_number: u16,
}

impl InternalResetCommand {
    pub fn session(&self) -> &str {
        self.session.as_str()
    }
    pub fn serial_number(&self) -> u16 {
        self.serial_number
    }
}

impl From<Contract<'_>> for InternalContract {
    fn from(contract: Contract<'_>) -> InternalContract {
        let original_bytes = contract._tab.buf().to_vec();
        let verifying_key = VerifyingKey::from_sec1_bytes(contract.public_key().unwrap().bytes())
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
            public_key: Some(verifying_key),
            original_bytes,
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

impl From<AbortCommand<'_>> for InternalAbortCommand {
    fn from(abort_command: AbortCommand) -> InternalAbortCommand {
        Self {
            contract_serial_number: abort_command.contract_serial_number(),
            serial_number: abort_command.serial_number(),
            counter: abort_command.counter(),
        }
    }
}

impl From<Bot<'_>> for InternalBot {
    fn from(bot: Bot) -> InternalBot {
        let internal_permission: Option<InternalPermission> =
            bot.permissions().as_ref().map(|p| (*p).into());

        let verifying_key = VerifyingKey::from_sec1_bytes(bot.public_key().unwrap().bytes())
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
            can_release: permission.can_release(),
        }
    }
}

impl From<ResetCommand<'_>> for InternalResetCommand {
    fn from(reset: ResetCommand) -> InternalResetCommand {
        Self {
            session: reset.session().unwrap().to_string(),
            serial_number: reset.serial_number(),
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
