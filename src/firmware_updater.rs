use crate::firmware_generated::club;
use crate::firmware_generated::club::subjugated::fb::message::firmware::{
    FirmwareChallengeResponse, FirmwareChallengeResponseArgs, FirmwareMessageArgs,
    GetLatestFirmwareResponse, MessagePayload,
};
use crate::generated::generated::get_challenge_key;
use crate::internal_firmware::{FirmwareMessageType, InternalChallenge};
use crate::mqtt_service::SignedMessageTransport;
use crate::mqtt_service::TopicType::FirmwareMessage;
use anyhow::{anyhow, Context};
use embedded_svc::ota::{Ota, Slot, SlotState};
use esp_idf_hal::cpu::Core::Core1;
use esp_idf_hal::sys::EspError;
use esp_idf_hal::task::thread::ThreadSpawnConfiguration;
use esp_idf_svc::ota::{EspOta, EspOtaUpdate};
use flatbuffers::FlatBufferBuilder;
use p256::ecdsa::signature::hazmat::PrehashSigner;
use p256::ecdsa::{Signature, SigningKey};
use p256::SecretKey;
use std::cell::RefCell;
use std::collections::VecDeque;
use std::marker::PhantomData;
use std::rc::Rc;
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;

pub struct FirmwareAssembler {
    pub current_message: Box<Vec<u8>>,
    pub started: bool,
}

impl FirmwareAssembler {
    pub fn new() -> Self {
        Self {
            current_message: Box::new(vec![]),
            started: false,
        }
    }

    pub fn new_with_size(size: usize) -> Self {
        Self {
            current_message: Box::new(Vec::with_capacity(size)),
            started: false,
        }
    }

    pub fn current_size(&self) -> usize {
        self.current_message.len()
    }

    pub fn add_to_current_message(&mut self, mut data: Vec<u8>) {
        self.started = true;
        self.current_message.extend(data.drain(0..));
        log::info!("Message size up to {:?}", self.current_message.len());
    }
}

pub fn read_as_firmware_message(data: &[u8]) -> Result<FirmwareMessageType, String> {
    match club::subjugated::fb::message::firmware::root_as_firmware_message(data) {
        Ok(firmware_message) => match firmware_message.payload_type() {
            MessagePayload::FirmwareChallengeRequest => {
                if let Some(challenge) = firmware_message.payload_as_firmware_challenge_request() {
                    let copy = challenge.nonce().unwrap();
                    let mut b: Vec<u8> = Vec::new();
                    copy.iter().for_each(|x| b.push(x));

                    let challenge = InternalChallenge::new(b, firmware_message.request_id());
                    Ok(FirmwareMessageType::Challenge(challenge))
                } else {
                    Err("Couldn't parse".to_string())
                }
            }
            MessagePayload::GetLatestFirmwareResponse => Ok(FirmwareMessageType::FirmwareResponse),
            _ => Err("Unhandled".to_string()),
        },
        Err(e) => {
            log::info!("Couldn't parse firmware message: {:?}", e);
            Err("Couldn't parse firmware message".to_string())
        }
    }
}

pub struct FirmwareManager {}

impl FirmwareManager {
    pub fn new() -> Self {
        Self {}
    }

    pub fn respond_to_challenge(
        &self,
        challenge: &InternalChallenge,
        session_token: &String,
    ) -> Vec<u8> {
        let mut builder = FlatBufferBuilder::with_capacity(1024);

        let signing_key = get_challenge_key();
        let signature_offset = if let Some(key) = signing_key {
            let sized_nonce: &[u8; 32] = &challenge.nonce.as_slice().try_into().unwrap();
            let signature: Signature = key
                .sign_prehash(sized_nonce)
                .expect("Nonce should be 32 bytes and fixed");
            let signature_bytes = signature.to_bytes();

            Some(builder.create_vector(&signature_bytes.as_slice()))
        } else {
            None
        };

        let sessionOffset = builder.create_string(&session_token);

        let firmware_challenge_offset = FirmwareChallengeResponse::create(
            &mut builder,
            &FirmwareChallengeResponseArgs {
                signature: signature_offset,
                version: None,
            },
        );

        let firmware_offset = club::subjugated::fb::message::firmware::FirmwareMessage::create(
            &mut builder,
            &FirmwareMessageArgs {
                payload_type: MessagePayload::FirmwareChallengeResponse,
                payload: Some(firmware_challenge_offset.as_union_value()),
                request_id: challenge.request_id,
                session_token: Some(sessionOffset),
            },
        );

        builder.finish(firmware_offset, None);
        builder.finished_data().to_vec()
    }
}

pub struct FirmwareUpdater {
    incoming_bytes: Arc<Mutex<VecDeque<Vec<u8>>>>,
    complete: Arc<Mutex<bool>>,
}

impl FirmwareUpdater {
    pub fn new() -> Self {
        let mut updater = Self {
            incoming_bytes: Arc::new(Mutex::new(VecDeque::new())),
            complete: Arc::new(Mutex::new(false)),
        };

        let queue_ref = Arc::clone(&updater.incoming_bytes);
        let complete_ref = Arc::clone(&updater.complete);

        ThreadSpawnConfiguration {
            name: Some("firmware-thread\x00".as_bytes()),
            stack_size: 5000,
            // priority: 15,
            pin_to_core: Some(Core1),
            ..Default::default()
        }
        .set()
        .unwrap();

        thread::spawn(move || {
            let mut ota = EspOta::new().expect("Ota new");
            match ota.get_boot_slot() {
                Ok(slot) => {
                    log::info!("Firmware updater got boot slot {:?}", slot);
                }
                Err(_) => {}
            }

            match ota.get_running_slot() {
                Ok(slot) => {
                    log::info!("Firmware updater got running slot {:?}", slot);
                }
                Err(_) => {}
            }

            match ota.get_update_slot() {
                Ok(slot) => {
                    log::info!("Firmware updater got update slot {:?}", slot);
                }
                Err(e) => {
                    log::error!("Firmware updater FAILED update slot {:?}", e);
                }
            }

            match ota.initiate_update() {
                Ok(mut update) => {
                    log::info!("Have a working update :-)");

                    loop {
                        if let Ok(complete_flag) = complete_ref.lock() {
                            if *complete_flag {
                                log::info!("OTA update complete");
                                break;
                            }
                        }

                        let mut bytes_to_write: VecDeque<Vec<u8>> =
                            queue_ref.lock().unwrap().drain(..).collect();
                        while !bytes_to_write.is_empty() {
                            let data = bytes_to_write.pop_front().unwrap();
                            // log::info!("FIRMWARE Writing {} bytes", data.len());
                            match update.write(data.as_slice()) {
                                Ok(_) => {
                                    log::error!("FIRMWARE Write Success");
                                }
                                Err(e) => {
                                    log::error!("FIRMWARE Write Error: {:?}", e);
                                }
                            }
                        }
                        std::thread::sleep(Duration::from_millis(800));
                    }
                }
                Err(e) => {
                    log::error!("Update failed: {}", e);
                }
            };
        });

        ThreadSpawnConfiguration {
            ..Default::default()
        }
        .set()
        .unwrap();

        updater
    }

    pub fn queue_bytes(&self, bytes: Vec<u8>) {
        if let Ok(mut incoming_bytes) = self.incoming_bytes.lock() {
            incoming_bytes.push_back(bytes);
        }
    }

    fn get_running_version() -> anyhow::Result<heapless::String<24>> {
        let ota = EspOta::new().expect("Ota new");

        if let Ok(slot) = ota.get_running_slot() {
            if let Some(firmware) = slot.firmware {
                Ok(firmware.version)
            } else {
                Err(anyhow!("Failed to get firmware version"))
            }
        } else {
            Err(anyhow!("Failed to get firmware version"))
        }
    }

    pub fn do_something() -> anyhow::Result<()> {
        let mut ota = EspOta::new()?;
        let running_slot = ota.get_running_slot()?;

        if running_slot.state == SlotState::Factory {
            log::info!("Factory slot can't be marked");
            return Ok(());
        }

        if running_slot.state != SlotState::Valid {
            let is_app_valid = true;

            // Do the necessary checks to validate that your app is working as expected.
            // For example, you can contact your API to verify you still have access to it.

            if is_app_valid {
                ota.mark_running_slot_valid()?;
            } else {
                ota.mark_running_slot_invalid_and_reboot();
            }
        }

        Ok(())
    }
}
