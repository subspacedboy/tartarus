use std::io::Read;
use hkdf::hmac::Mac;
use p256::ecdsa::{Signature, VerifyingKey};
use p256::ecdsa::signature::Verifier;
use p256::PublicKey;
use sha2::{Digest, Sha256};
use crate::contract_generated::club::subjugated::fb::message::{Contract, MessagePayload};
use crate::internal_contract::{InternalContract, InternalLockCommand, InternalUnlockCommand, InternalUnverified};

pub(crate) struct SignedMessageVerifier;

#[derive(Debug, Clone)]
pub enum VerifiedType {
    Contract(InternalContract),
    UnlockCommand(InternalUnlockCommand),
    LockCommand(InternalLockCommand),
    NoVerifiedType
}

#[derive(Debug, Clone)]
pub struct VerificationError {
    pub serial_number: u16,
    pub counter: u16,
    pub message: String,
}

impl SignedMessageVerifier {
    pub fn new() -> Self {
        Self {}
    }

    pub fn verify(self, incoming_data: Vec<u8>, session_token: &String, contract_public_key: Option<&VerifyingKey>) -> Result<VerifiedType, VerificationError> {
        let mut result_contract : Option<Contract> = None;
        let owned_data = incoming_data.clone();

        match crate::contract_generated::club::subjugated::fb::message::root_as_signed_message(owned_data.as_slice()) {
            Ok(signed_msg) => {
                log::info!("Signed message: {:?}", signed_msg);

                let signature = signed_msg.signature();
                log::debug!("Signature: {:?}", signature);

                // if let Some(payload) = signed_msg.payload_type() {
                    match signed_msg.payload_type() {
                        MessagePayload::Contract => {
                            log::debug!("Proceeding as contract");
                            let contract = signed_msg.payload_as_contract().unwrap();
                            log::debug!("Contract Public Key: {:?}", contract.public_key().unwrap());

                            match PublicKey::from_sec1_bytes(contract.public_key().unwrap().bytes()) {
                                Ok(_valid_key) => {
                                    log::debug!("Key correctly parsed!");

                                    let contract_table_start = contract._tab.loc();
                                    let vtable_offset = u16::from_le_bytes(incoming_data[contract_table_start..contract_table_start+2].try_into().expect("wrong size")) as usize;
                                    let contract_end = incoming_data.len();
                                    let vtable_start = contract_table_start - vtable_offset;
                                    let contract_buffer = &incoming_data[vtable_start..contract_end];

                                    log::debug!("Contract buffer w/ vtable ({},{}): {:?}", vtable_start, contract_end, contract_buffer.bytes());
                                    let hash = Sha256::digest(&contract_buffer);
                                    log::debug!("Hash: {:?}", hash);

                                    let vkey = VerifyingKey::from_sec1_bytes(contract.public_key().unwrap().bytes()).expect("Valid public key");
                                    match Signature::from_bytes(signature.unwrap().bytes().into()) {
                                        Ok(valid_signature) => {
                                            log::debug!("Signature was valid: {:?}", valid_signature);
                                            match vkey.verify(&hash, &valid_signature) {
                                                Ok(_) => {
                                                    log::debug!("Signature verified!");

                                                    return Ok(VerifiedType::Contract(contract.into()));
                                                }
                                                Err(e) => {
                                                    log::debug!("Error verifying signature: {:?}", e);
                                                }
                                            }
                                        }
                                        Err(e) => {
                                            log::debug!("Error parsing signature: {:?}", e);
                                        }
                                    }

                                }
                                Err(e) => {
                                    log::debug!("Error parsing key: {:?}", e);
                                }
                            }
                        },
                        MessagePayload::UnlockCommand => {
                            log::info!("Processing unlock command");
                            let unlock = signed_msg.payload_as_unlock_command().unwrap();
                            if let Some(public_key) = contract_public_key {
                                let message_table_start = unlock._tab.loc();
                                let vtable_offset = u16::from_le_bytes(incoming_data[message_table_start..message_table_start +2].try_into().expect("wrong size")) as usize;
                                let contract_end = incoming_data.len();
                                let vtable_start = message_table_start - vtable_offset;
                                let message_buffer = &incoming_data[vtable_start..contract_end];

                                log::info!("Unlock buffer w/ vtable ({},{}): {:?}", vtable_start, contract_end, message_buffer.bytes());
                                let hash = Sha256::digest(&message_buffer);
                                log::info!("Hash: {:?}", hash);

                                match Signature::from_bytes(signature.unwrap().bytes().into()) {
                                    Ok(valid_signature) => {
                                        if let Some(verifying_key) = contract_public_key {
                                            match verifying_key.verify(&hash, &valid_signature) {
                                                Ok(_) => {
                                                    log::debug!("Signature verified!");
                                                    return Ok(VerifiedType::UnlockCommand(unlock.into()));
                                                }
                                                Err(e) => {
                                                    log::error!("Error verifying signature: {:?}", e);
                                                }
                                            }
                                        } else {
                                            log::error!("Somehow contract publish key wasn't supplied");
                                        }
                                    }
                                    Err(e) => {
                                        log::error!("Error parsing signature: {:?}", e);
                                    }
                                }

                            } else {
                                log::error!("We received a message that requires a contract without having a public key already loaded.");
                                return Err(VerificationError {
                                    serial_number: unlock.serial_number(),
                                    counter: unlock.counter(),
                                    message: "Required a contract key but none was loaded".to_string(),
                                })
                            }
                        },
                        MessagePayload::LockCommand => {
                            log::info!("Processing lock command");
                            let lock = signed_msg.payload_as_lock_command().unwrap();
                            if let Some(public_key) = contract_public_key {
                                let message_table_start = lock._tab.loc();
                                let vtable_offset = u16::from_le_bytes(incoming_data[message_table_start..message_table_start +2].try_into().expect("wrong size")) as usize;
                                let contract_end = incoming_data.len();
                                let vtable_start = message_table_start - vtable_offset;
                                let message_buffer = &incoming_data[vtable_start..contract_end];

                                log::info!("Unlock buffer w/ vtable ({},{}): {:?}", vtable_start, contract_end, message_buffer.bytes());
                                let hash = Sha256::digest(&message_buffer);
                                log::info!("Hash: {:?}", hash);

                                match Signature::from_bytes(signature.unwrap().bytes().into()) {
                                    Ok(valid_signature) => {
                                        if let Some(verifying_key) = contract_public_key {
                                            match verifying_key.verify(&hash, &valid_signature) {
                                                Ok(_) => {
                                                    log::debug!("Signature verified!");
                                                    return Ok(VerifiedType::LockCommand(lock.into()));
                                                }
                                                Err(e) => {
                                                    log::error!("Error verifying signature: {:?}", e);
                                                }
                                            }
                                        } else {
                                            log::error!("Somehow contract publish key wasn't supplied");
                                        }
                                    }
                                    Err(e) => {
                                        log::error!("Error parsing signature: {:?}", e);
                                    }
                                }

                            } else {
                                log::error!("We received a message that requires a contract without having a public key already loaded.");
                                return Err(VerificationError {
                                    serial_number: lock.serial_number(),
                                    counter: lock.counter(),
                                    message: "Required a contract key but none was loaded".to_string(),
                                })
                            }
                        }
                        _ => {
                            // return Ok(VerifiedType::NoVerifiedType);
                        },
                    // }

                }
            }
            Err(e) => {
                println!("Error parsing signed message: {:?}", e);
            }
        }

        Ok(VerifiedType::NoVerifiedType)
    }
}