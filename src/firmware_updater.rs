use crate::firmware_generated::club;
use crate::firmware_generated::club::subjugated::fb::message::firmware::{
    FirmwareChallengeResponse, FirmwareChallengeResponseArgs, FirmwareMessage, FirmwareMessageArgs,
    GetFirmwareChunkRequest, GetFirmwareChunkRequestArgs, GetLatestFirmwareRequest,
    GetLatestFirmwareRequestArgs, MessagePayload, Version, VersionArgs,
};
use crate::generated::get_challenge_key;
use crate::internal_firmware::{
    FirmwareMessageType, InternalChallenge, InternalFirmwareChunk, InternalFirmwareResponse,
};
use std::borrow::BorrowMut;
use std::cell::RefCell;

use crate::lock_ctx::LockCtx;
use crate::mqtt_service::{MqttService, SignedMessageTransport, TopicType};
use crate::Esp32Rng;
use anyhow::anyhow;
use embedded_svc::ota::SlotState;
use esp_idf_hal::cpu::Core::Core1;
use esp_idf_hal::task::thread::ThreadSpawnConfiguration;
use esp_idf_svc::ota::EspOta;
use flatbuffers::FlatBufferBuilder;
use p256::ecdsa::signature::hazmat::PrehashSigner;
use p256::ecdsa::Signature;
use rand_core::RngCore;
use sha2::{Digest, Sha256};
use std::collections::VecDeque;
use std::rc::Rc;
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;

pub struct FirmwareAssembler {
    pub current_message: Vec<u8>,
    pub started: bool,
}

impl FirmwareAssembler {
    pub fn new() -> Self {
        Self {
            current_message: vec![],
            started: false,
        }
    }

    pub fn new_with_size(size: usize) -> Self {
        Self {
            current_message: Vec::with_capacity(size),
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
            MessagePayload::GetLatestFirmwareResponse => {
                if let Some(response) = firmware_message.payload_as_get_latest_firmware_response() {
                    Ok(FirmwareMessageType::FirmwareResponse(
                        InternalFirmwareResponse::new(
                            response.firmware_name().unwrap().to_string(),
                            response.version_name().unwrap().to_string(),
                            response.size_() as usize,
                        ),
                    ))
                } else {
                    Err("Couldn't parse".to_string())
                }
            }
            MessagePayload::GetFirmwareChunkResponse => {
                if let Some(chunk) = firmware_message.payload_as_get_firmware_chunk_response() {
                    let copy = chunk.chunk().unwrap();
                    let mut b: Vec<u8> = Vec::new();
                    copy.iter().for_each(|x| b.push(x));

                    Ok(FirmwareMessageType::FirmwareChunk(
                        InternalFirmwareChunk::new(
                            b,
                            chunk.size_() as usize,
                            chunk.offset() as usize,
                        ),
                    ))
                } else {
                    Err("Couldn't parse".to_string())
                }
            }
            _ => Err("Unhandled".to_string()),
        },
        Err(e) => {
            log::info!("Couldn't parse firmware message: {:?}", e);
            Err("Couldn't parse firmware message".to_string())
        }
    }
}

pub struct FirmwareManager {
    pub total_size: usize,
    pub acked_size: usize,
    session_token: Option<String>,
    current_running_firmware: Option<String>,
    next_firmware_version: Option<String>,

    next_firmware_name: Option<String>,
    next_firmware_size: Option<usize>,

    currently_updating_digest: Option<Sha256>,
    firmware_updater: Option<FirmwareUpdater>,
    mqtt_service: Option<Rc<RefCell<MqttService>>>,
}

impl FirmwareManager {
    pub fn new() -> Self {
        Self {
            total_size: 0,
            acked_size: 0,
            next_firmware_name: None,
            next_firmware_version: None,
            currently_updating_digest: None,
            firmware_updater: None,
            current_running_firmware: None,
            session_token: None,
            mqtt_service: None,
            next_firmware_size: None,
        }
    }

    pub fn set_session_token(&mut self, session_token: String) {
        self.session_token = Some(session_token);
    }

    pub fn set_mqtt_service(&mut self, mqtt_service: Rc<RefCell<MqttService>>) {
        self.mqtt_service = Some(mqtt_service);
    }

    pub fn process_message(&mut self, firmware_data: Vec<u8>, under_contract: bool) {
        if let Ok(msg) = read_as_firmware_message(firmware_data.as_slice()) {
            match msg {
                FirmwareMessageType::Challenge(challenge) => {
                    log::info!("We got firmware challenge");
                    let response_bytes =
                        self.respond_to_challenge(&challenge, self.session_token.as_ref().unwrap());
                    self.enqueue_message(SignedMessageTransport::new(
                        response_bytes,
                        crate::mqtt_service::TopicType::FirmwareMessage,
                    ));
                }
                FirmwareMessageType::FirmwareResponse(internal_response) => {
                    log::info!(
                        "Got latest firmware info -> Name {}",
                        internal_response.firmware_name
                    );

                    self.next_firmware_name = Some(internal_response.firmware_name.clone());
                    self.next_firmware_version = Some(internal_response.version_name.clone());
                    self.next_firmware_size = Some(internal_response.size);
                }
                FirmwareMessageType::FirmwareChunk(chunk) => {
                    log::info!(
                        "We asked for a chunk and we got a chunk [Size={}, Offset={}]",
                        chunk.size(),
                        chunk.offset()
                    );
                    self.ack_chunk(chunk.size(), chunk.data());

                    if self.needs_more() {
                        let request_bytes =
                            self.request_firmware_chunk(self.session_token.as_ref().unwrap());
                        self.enqueue_message(SignedMessageTransport::new(
                            request_bytes,
                            crate::mqtt_service::TopicType::FirmwareMessage,
                        ));
                    } else {
                        log::info!(
                            "Got the firmware image: bytes {} of {}",
                            self.acked_size,
                            self.total_size
                        );
                        match self.finalize() {
                            Ok(digest) => {
                                log::info!("Firmware digest: bytes {} ", digest);
                            }
                            Err(..) => {
                                log::info!("Couldn't get firmware digest...");
                            }
                        }
                    }
                }
            }
        }
    }

    pub fn initiate_firmware_update(&mut self) {
        if self.is_update_available() {
            self.start_requesting_firmware(
                self.next_firmware_size.unwrap(),
                self.next_firmware_name.as_ref().unwrap().clone(),
            );

            let request_bytes = self.request_firmware_chunk(self.session_token.as_ref().unwrap());
            self.enqueue_message(SignedMessageTransport::new(
                request_bytes,
                TopicType::FirmwareMessage,
            ));
        } else {
            log::info!("Not updating firmware");
        }
    }

    fn enqueue_message(&mut self, message: SignedMessageTransport) {
        if let Some(mqtt_service) = &mut self.mqtt_service {
            let service = mqtt_service.borrow_mut();
            let mqtt = service.as_ref().borrow_mut();
            mqtt.enqueue_message(message);
        } else {
            log::warn!("Enqueued a message but we don't have an MQTT service");
        }
    }

    pub fn respond_to_challenge(
        &self,
        challenge: &InternalChallenge,
        session_token: &str,
    ) -> Vec<u8> {
        let mut builder = FlatBufferBuilder::with_capacity(1024);

        let signing_key = get_challenge_key();
        let signature_offset = if let Some(key) = signing_key {
            let sized_nonce: &[u8; 32] = &challenge.nonce.as_slice().try_into().unwrap();
            let signature: Signature = key
                .sign_prehash(sized_nonce)
                .expect("Nonce should be 32 bytes and fixed");
            let signature_bytes = signature.to_bytes();

            Some(builder.create_vector(signature_bytes.as_slice()))
        } else {
            None
        };

        let session_offset = builder.create_string(session_token);

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
                session_token: Some(session_offset),
            },
        );

        builder.finish(firmware_offset, None);
        builder.finished_data().to_vec()
    }

    pub fn start_requesting_firmware(&mut self, total_size: usize, name: String) {
        self.total_size = total_size;
        self.next_firmware_name = Some(name);
        self.currently_updating_digest = Some(Sha256::new());
        self.firmware_updater = Some(FirmwareUpdater::new());
    }

    pub fn ack_chunk(&mut self, size: usize, data: &[u8]) {
        self.acked_size += size;
        if let Some(digest) = &mut self.currently_updating_digest {
            let start = &data[..5.min(data.len())];
            let end = &data[data.len().saturating_sub(5)..];
            log::info!("Chunk [First 5={:02x?}, Last 5={:02x?}]", start, end);
            Digest::update(digest, data);
        }

        if let Some(updater) = &mut self.firmware_updater {
            if let Ok(queue) = &mut updater.incoming_bytes.lock() {
                queue.push_back(data.to_vec());
            }
        }
    }

    pub fn needs_more(&self) -> bool {
        if self.acked_size > self.total_size {
            log::warn!("Acked size is greater than total size");
        }
        log::info!(
            "Acked size is {}, total size {}",
            self.acked_size,
            self.total_size
        );
        self.acked_size != self.total_size
    }

    /// Finish pushing data to the firmware updater thread. Must be called _after_ the final
    /// ack_chunk.
    pub fn finalize(&mut self) -> Result<String, String> {
        if let Some(updater) = &mut self.firmware_updater {
            if let Ok(mut complete_flag) = updater.complete.lock() {
                *complete_flag = true;
            }
        }

        let o = self.currently_updating_digest.take();
        if let Some(digest) = o {
            let value = digest.finalize();
            Ok(data_encoding::HEXLOWER.encode(&value))
        } else {
            Err("No digest".to_string())
        }
    }

    pub fn request_firmware_chunk(&self, session_token: &str) -> Vec<u8> {
        let mut builder = FlatBufferBuilder::with_capacity(1024);

        let firmware_name_offset = builder.create_string(self.next_firmware_name.as_ref().unwrap());
        let session_offset = builder.create_string(session_token);

        let request_offset = if self.acked_size == 0 {
            0
        } else {
            self.acked_size
        };

        let get_chunk_offset = GetFirmwareChunkRequest::create(
            &mut builder,
            &GetFirmwareChunkRequestArgs {
                firmware_name: Some(firmware_name_offset),
                offset: request_offset as i32,
                size_: 16 * 1024,
            },
        );

        let mut rng = Esp32Rng;
        let request_id = rng.next_u64() as i64;

        let firmware_offset = club::subjugated::fb::message::firmware::FirmwareMessage::create(
            &mut builder,
            &FirmwareMessageArgs {
                payload_type: MessagePayload::GetFirmwareChunkRequest,
                payload: Some(get_chunk_offset.as_union_value()),
                request_id,
                session_token: Some(session_offset),
            },
        );

        builder.finish(firmware_offset, None);
        builder.finished_data().to_vec()
    }

    pub fn enqueue_get_latest_firmware(&mut self) {
        let mut builder = FlatBufferBuilder::with_capacity(1024);

        let current_version = self.get_running_version().unwrap();
        let current_version_offset = builder.create_string(current_version.as_str());

        let version = Version::create(
            &mut builder,
            &VersionArgs {
                name: None,
                signature: None,
                version_name: Some(current_version_offset),
            },
        );

        let session = builder.create_string(self.session_token.as_ref().unwrap());

        let get_latest_firmware_request_offset = GetLatestFirmwareRequest::create(
            &mut builder,
            &GetLatestFirmwareRequestArgs {
                version: Some(version),
            },
        );

        let mut rng = Esp32Rng;
        let request_id = rng.next_u64();

        let firmware_message = FirmwareMessage::create(
            &mut builder,
            &FirmwareMessageArgs {
                payload_type: MessagePayload::GetLatestFirmwareRequest,
                payload: Some(get_latest_firmware_request_offset.as_union_value()),
                request_id: request_id as i64,
                session_token: Some(session),
            },
        );

        builder.finish(firmware_message, None);
        let data = builder.finished_data().to_vec();

        let t = SignedMessageTransport::new(data, TopicType::FirmwareMessage);

        self.enqueue_message(t);
    }

    pub fn is_update_available(&mut self) -> bool {
        if self.next_firmware_version.is_none() {
            log::info!("No next firmware version during check");
            return false;
        }

        let current_version = self.get_running_version().unwrap();
        log::info!(
            "Performing check on {} vs {}",
            current_version,
            self.next_firmware_version.as_ref().unwrap()
        );
        current_version.as_str() != self.next_firmware_version.as_ref().unwrap()
    }

    pub fn next_firmware_version(&mut self) -> String {
        self.next_firmware_version.as_ref().unwrap().clone()
    }

    pub fn sanity_check_or_abort(&self) -> anyhow::Result<()> {
        let mut ota = EspOta::new()?;
        let running_slot = ota.get_running_slot()?;

        if running_slot.state == SlotState::Factory {
            log::info!("Factory slot can't be marked");
            return Ok(());
        }

        if running_slot.state != SlotState::Valid {
            log::info!("Sanity checking firmware after OTA update.");
            let is_app_valid = true;

            // Is there something specific we can do to make sure it's valid?

            if is_app_valid {
                log::info!("Marking valid.");
                ota.mark_running_slot_valid()?;
            } else {
                ota.mark_running_slot_invalid_and_reboot();
            }
        }

        Ok(())
    }

    pub fn get_running_version(&mut self) -> anyhow::Result<String> {
        if self.current_running_firmware.is_none() {
            let ota = EspOta::new().expect("Ota new");

            if let Ok(slot) = ota.get_running_slot() {
                if let Some(firmware) = slot.firmware {
                    self.current_running_firmware = Some(firmware.version.to_string());
                    Ok(firmware.version.to_string())
                } else {
                    Err(anyhow!("Failed to get firmware version"))
                }
            } else {
                Err(anyhow!("Failed to get firmware version"))
            }
        } else {
            let current_running = self.current_running_firmware.as_ref().unwrap();
            Ok(current_running.clone())
        }
    }
}

pub struct FirmwareUpdater {
    incoming_bytes: Arc<Mutex<VecDeque<Vec<u8>>>>,
    complete: Arc<Mutex<bool>>,
}

impl FirmwareUpdater {
    pub fn new() -> Self {
        let updater = Self {
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

            if let Ok(slot) = ota.get_boot_slot() {
                log::info!("Firmware updater got boot slot {:?}", slot);
            }

            if let Ok(slot) = ota.get_running_slot() {
                log::info!("Firmware updater got running slot {:?}", slot);
            }

            match ota.get_update_slot() {
                Ok(slot) => {
                    log::info!("Firmware updater got update slot {:?}", slot);
                }
                Err(e) => {
                    log::error!("Firmware updater FAILED update slot {:?}", e);
                }
            }

            let mut local_digest = Sha256::new();

            match ota.initiate_update() {
                Ok(mut update) => {
                    log::info!("Have a working update :-)");

                    loop {
                        let mut bytes_to_write: VecDeque<Vec<u8>> =
                            queue_ref.lock().unwrap().drain(..).collect();
                        while !bytes_to_write.is_empty() {
                            let data = bytes_to_write.pop_front().unwrap();
                            match update.write(data.as_slice()) {
                                Ok(_) => {
                                    Digest::update(&mut local_digest, data.as_slice());
                                    log::info!("FIRMWARE Write Success");
                                }
                                Err(e) => {
                                    log::error!("FIRMWARE Write Error: {:?}", e);
                                }
                            }
                        }

                        if let Ok(complete_flag) = complete_ref.lock() {
                            if *complete_flag {
                                log::info!("OTA update complete");
                                break;
                            }
                        }
                        std::thread::sleep(Duration::from_millis(400));
                    } // Loop

                    log::info!("Preparing to complete");
                    let value = local_digest.finalize();
                    log::info!("Data we wrote: {}", data_encoding::HEXLOWER.encode(&value));

                    match update.complete() {
                        Ok(_) => {
                            log::info!("Firmware update complete. Resetting system.");
                            esp_idf_svc::hal::reset::restart();
                        }
                        Err(e) => {
                            log::error!("Unable to complete firmware?! {:?}", e);
                        }
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
}
