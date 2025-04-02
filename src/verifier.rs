use std::io::Read;
use p256::ecdsa::{Signature, VerifyingKey};
use p256::ecdsa::signature::Verifier;
use p256::PublicKey;
use sha2::{Digest, Sha256};
use crate::contract_generated::club::subjugated::fb::message::{Contract, MessagePayload, PartialContract};
use crate::internal_contract::{InternalContract, InternalPartialContract};
use crate::State;

pub(crate) struct ContractVerifier;

pub enum VerifiedType {
    Contract(InternalContract),
    PartialContract(InternalPartialContract),
    NoVerifiedType
}

impl ContractVerifier {
    pub fn new() -> Self {
        Self {}
    }

    pub fn verify(self, incoming_data: Vec<u8>, session_token: &String) -> Result<VerifiedType, String> {
        let mut result_partial_contract : Option<PartialContract> = None;
        let mut result_contract : Option<Contract> = None;
        let owned_data = incoming_data.clone();

        match crate::contract_generated::club::subjugated::fb::message::root_as_signed_message(owned_data.as_slice()) {
            Ok(signed_msg) => {
                log::info!("Signed message: {:?}", signed_msg);

                let signature = signed_msg.signature();
                log::debug!("Signature: {:?}", signature);

                // if let Some(payload) = signed_msg.payload_type() {
                    match signed_msg.payload_type() {
                        MessagePayload::PartialContract => {
                            // Step 4: Extract the Contract
                            let partial_contract = signed_msg.payload_as_partial_contract().unwrap();
                            log::debug!("Contract Public Key: {:?}", partial_contract.public_key().unwrap());

                            match PublicKey::from_sec1_bytes(partial_contract.public_key().unwrap().bytes()) {
                                Ok(_valid_key) => {
                                    log::debug!("Key correctly parsed!");

                                    let contract_table_start = partial_contract._tab.loc();
                                    let vtable_offset = u16::from_le_bytes(incoming_data[contract_table_start..contract_table_start+2].try_into().expect("wrong size")) as usize;
                                    let contract_end = incoming_data.len();
                                    let vtable_start = contract_table_start - vtable_offset;
                                    let contract_buffer = &incoming_data[vtable_start..contract_end];

                                    log::debug!("Contract buffer w/ vtable ({},{}): {:?}", vtable_start, contract_end, contract_buffer.bytes());
                                    let hash = Sha256::digest(&contract_buffer);
                                    log::debug!("Hash: {:?}", hash);

                                    let vkey = VerifyingKey::from_sec1_bytes(partial_contract.public_key().unwrap().bytes()).expect("Valid public key");
                                    match Signature::from_bytes(signature.unwrap().bytes().into()) {
                                        Ok(valid_signature) => {
                                            log::debug!("Signature was valid: {:?}", valid_signature);
                                            match vkey.verify(&hash, &valid_signature) {
                                                Ok(_) => {
                                                    log::debug!("Signature verified!");
                                                    result_partial_contract = Some(partial_contract)
                                                    // return Ok(VerifiedType::PartialContract(incoming_data, partial_contract));
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
                        }
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