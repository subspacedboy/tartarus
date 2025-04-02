use crate::contract_generated::club::subjugated::fb::message::{
    Contract, LockCommand, MessagePayload, ReleaseCommand, UnlockCommand,
};
use crate::internal_contract::{
    InternalContract, InternalLockCommand, InternalReleaseCommand, InternalUnlockCommand,
};
use aes_gcm::aes::cipher::crypto_common::Output;
use p256::ecdsa::signature::Verifier;
use p256::ecdsa::{Signature, VerifyingKey};
use sha2::{Digest, Sha256};
use std::io::Read;

pub(crate) struct SignedMessageVerifier;

#[derive(Debug, Clone)]
pub enum VerifiedType {
    Contract(InternalContract),
    UnlockCommand(InternalUnlockCommand),
    LockCommand(InternalLockCommand),
    ReleaseCommand(InternalReleaseCommand),
}

#[derive(Debug, Clone)]
pub struct VerificationError {
    pub serial_number: u16,
    pub counter: u16,
    pub message: String,
}

trait HasTable {
    fn loc(&self) -> usize;
}
macro_rules! impl_has_table {
    ($($t:ty),*) => {
        $(impl HasTable for $t {
            fn loc(&self) -> usize {
                self._tab.loc()
            }
        })*
    };
}
impl_has_table!(
    Contract<'_>,
    UnlockCommand<'_>,
    LockCommand<'_>,
    ReleaseCommand<'_>
);

pub trait HashTableBytes<T> {
    fn calculate_hash(&self, incoming_data: &Vec<u8>) -> Output<Sha256>;
}

impl<T: HasTable> HashTableBytes<T> for T {
    fn calculate_hash(&self, incoming_data: &Vec<u8>) -> Output<Sha256> {
        let message_table_start = self.loc();
        let vtable_offset = u16::from_le_bytes(
            incoming_data[message_table_start..message_table_start + 2]
                .try_into()
                .expect("wrong size"),
        ) as usize;
        let contract_end = incoming_data.len();
        let vtable_start = message_table_start - vtable_offset;
        let message_buffer = &incoming_data[vtable_start..contract_end];

        log::info!(
            "Unlock buffer w/ vtable ({},{}): {:?}",
            vtable_start,
            contract_end,
            message_buffer.bytes()
        );
        let hash: Output<Sha256> = Sha256::digest(&message_buffer);
        log::info!("Hash: {:?}", hash);
        hash
    }
}

impl SignedMessageVerifier {
    pub fn new() -> Self {
        Self {}
    }

    pub fn verify(
        self,
        incoming_data: Vec<u8>,
        contract_public_key: Option<&VerifyingKey>,
        minimum_counter: u16,
        contract_serial_number: u16,
    ) -> Result<VerifiedType, VerificationError> {
        let owned_data = incoming_data.clone();

        match crate::contract_generated::club::subjugated::fb::message::root_as_signed_message(
            owned_data.as_slice(),
        ) {
            Ok(signed_msg) => {
                log::info!("Signed message: {:?}", signed_msg);

                let signature_bytes = signed_msg.signature();
                log::debug!("Signature: {:?}", signature_bytes);
                let signature = match Signature::from_bytes(signature_bytes.unwrap().bytes().into())
                {
                    Ok(signature) => signature,
                    Err(e) => {
                        log::debug!("Error parsing signature: {:?}", e);
                        return Err(VerificationError {
                            serial_number: 0,
                            counter: 0,
                            message: "Signature bytes couldn't be parsed".to_string(),
                        });
                    }
                };
                match signed_msg.payload_type() {
                    MessagePayload::Contract => {
                        log::debug!("Proceeding as contract");
                        let contract = signed_msg.payload_as_contract().unwrap();
                        log::debug!("Contract Public Key: {:?}", contract.public_key().unwrap());
                        let verifying_key = match VerifyingKey::from_sec1_bytes(
                            contract.public_key().unwrap().bytes(),
                        ) {
                            Ok(valid_key) => valid_key,
                            Err(_) => {
                                return Err(VerificationError {
                                    serial_number: contract.serial_number(),
                                    counter: 0,
                                    message: "Invalid public key".to_string(),
                                })
                            }
                        };
                        let hash = contract.calculate_hash(&incoming_data);
                        match verifying_key.verify(&hash, &signature) {
                            Ok(_) => {
                                log::debug!("Signature verified!");
                                Ok(VerifiedType::Contract(contract.into()))
                            }
                            Err(_) => Err(VerificationError {
                                serial_number: contract.serial_number(),
                                counter: 0,
                                message: "Invalid signature".to_string(),
                            }),
                        }
                    }
                    MessagePayload::UnlockCommand => {
                        log::info!("Processing unlock command");
                        let unlock = signed_msg.payload_as_unlock_command().unwrap();

                        let verifying_key = match contract_public_key {
                            Some(verifying_key) => verifying_key,
                            None => {
                                log::error!("We received a message that requires a contract without having a public key already loaded.");
                                return Err(VerificationError {
                                    serial_number: unlock.serial_number(),
                                    counter: unlock.counter(),
                                    message: "Required a contract key but none was loaded"
                                        .to_string(),
                                });
                            }
                        };
                        let hash = unlock.calculate_hash(&incoming_data);
                        match verifying_key.verify(&hash, &signature) {
                            Ok(_) => {
                                log::debug!("Signature verified!");

                                if unlock.contract_serial_number() != contract_serial_number {
                                    return Err(VerificationError {
                                        serial_number: unlock.serial_number(),
                                        counter: unlock.counter(),
                                        message: format!("Serial number of command doesn't match current contract [{} vs {}]", unlock.serial_number(), contract_serial_number)
                                    });
                                }

                                if unlock.counter() > minimum_counter {
                                    Ok(VerifiedType::UnlockCommand(unlock.into()))
                                } else {
                                    Err(VerificationError {
                                        serial_number: unlock.serial_number(),
                                        counter: unlock.counter(),
                                        message: format!(
                                            "Counter on message was too low [{} vs {}]",
                                            unlock.counter(),
                                            minimum_counter
                                        ),
                                    })
                                }
                            }
                            Err(_) => Err(VerificationError {
                                serial_number: unlock.serial_number(),
                                counter: unlock.counter(),
                                message: "Invalid signature on unlock command".to_string(),
                            }),
                        }
                    }
                    MessagePayload::LockCommand => {
                        log::info!("Processing lock command");
                        let lock = signed_msg.payload_as_lock_command().unwrap();

                        let verifying_key = match contract_public_key {
                            Some(verifying_key) => verifying_key,
                            None => {
                                log::error!("We received a message that requires a contract without having a public key already loaded.");
                                return Err(VerificationError {
                                    serial_number: lock.serial_number(),
                                    counter: lock.counter(),
                                    message: "Required a contract key but none was loaded"
                                        .to_string(),
                                });
                            }
                        };
                        let hash = lock.calculate_hash(&incoming_data);
                        match verifying_key.verify(&hash, &signature) {
                            Ok(_) => {
                                log::debug!("Signature verified!");
                                if lock.contract_serial_number() != contract_serial_number {
                                    return Err(VerificationError {
                                        serial_number: lock.serial_number(),
                                        counter: lock.counter(),
                                        message: format!("Serial number of command doesn't match current contract [{} vs {}]", lock.serial_number(), contract_serial_number)
                                    });
                                }

                                if lock.counter() > minimum_counter {
                                    Ok(VerifiedType::LockCommand(lock.into()))
                                } else {
                                    Err(VerificationError {
                                        serial_number: lock.serial_number(),
                                        counter: lock.counter(),
                                        message: format!(
                                            "Counter on message was too low [{} vs {}]",
                                            lock.counter(),
                                            minimum_counter
                                        ),
                                    })
                                }
                            }
                            Err(_) => Err(VerificationError {
                                serial_number: lock.serial_number(),
                                counter: lock.counter(),
                                message: "Invalid signature on unlock command".to_string(),
                            }),
                        }
                    }
                    MessagePayload::ReleaseCommand => {
                        log::info!("Processing release command");
                        let release = signed_msg.payload_as_release_command().unwrap();

                        let verifying_key = match contract_public_key {
                            Some(verifying_key) => verifying_key,
                            None => {
                                log::error!("We received a message that requires a contract without having a public key already loaded.");
                                return Err(VerificationError {
                                    serial_number: release.serial_number(),
                                    counter: release.counter(),
                                    message: "Required a contract key but none was loaded"
                                        .to_string(),
                                });
                            }
                        };
                        let hash = release.calculate_hash(&incoming_data);
                        match verifying_key.verify(&hash, &signature) {
                            Ok(_) => {
                                log::debug!("Signature verified!");
                                if release.contract_serial_number() != contract_serial_number {
                                    return Err(VerificationError {
                                        serial_number: release.serial_number(),
                                        counter: release.counter(),
                                        message: format!("Serial number of command doesn't match current contract [{} vs {}]", release.serial_number(), contract_serial_number)
                                    });
                                }

                                if release.counter() > minimum_counter {
                                    Ok(VerifiedType::ReleaseCommand(release.into()))
                                } else {
                                    Err(VerificationError {
                                        serial_number: release.serial_number(),
                                        counter: release.counter(),
                                        message: format!(
                                            "Counter on message was too low [{} vs {}]",
                                            release.counter(),
                                            minimum_counter
                                        ),
                                    })
                                }
                            }
                            Err(_) => Err(VerificationError {
                                serial_number: release.serial_number(),
                                counter: release.counter(),
                                message: "Invalid signature on unlock command".to_string(),
                            }),
                        }
                    }
                    _ => Err(VerificationError {
                        serial_number: 0,
                        counter: 0,
                        message: "Unhandled message type".to_string(),
                    }),
                }
            }
            Err(_) => Err(VerificationError {
                serial_number: 0,
                counter: 0,
                message: "Message doesn't parse as a signed message".to_string(),
            }),
        }
    }
}
