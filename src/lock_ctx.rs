use crate::acknowledger::Acknowledger;
use crate::config_verifier::ConfigVerifier;
use crate::contract_generated::club::subjugated::fb::message::{
    MessagePayload, PeriodicUpdate, PeriodicUpdateArgs, SignedMessage, SignedMessageArgs,
    StartedUpdate, StartedUpdateArgs,
};
use crate::event_generated::club::subjugated::fb::event::{
    CommonMetadata, CommonMetadataArgs, Event, EventArgs, EventType, SignedEvent, SignedEventArgs,
};
use crate::fb_helper::calculate_signature;
#[allow(deprecated)]
use crate::firmware_updater::FirmwareManager;
use crate::internal_config::InternalConfig;
use crate::internal_contract::{InternalContract, InternalResetCommand, SaveState};
use crate::lock_ctx::TopicType::{Acknowledgments, ConfigurationData, SignedMessages};
use crate::mqtt_service::{MqttService, SignedMessageTransport, TopicType};
use crate::servo::Servo;
use crate::verifier::{SignedMessageVerifier, VerifiedType};
use crate::Esp32Rng;
use aes_gcm::aead::{Aead};
use aes_gcm::KeyInit;
use aes_gcm::{AeadCore, Aes256Gcm, Key};
use data_encoding::{BASE32_NOPAD, BASE64, BASE64URL};
use esp_idf_svc::nvs::{EspNvs, NvsDefault};
use esp_idf_svc::sys::{nvs_flash_deinit, nvs_flash_erase, nvs_flash_init};
use flatbuffers::FlatBufferBuilder;
use hkdf::Hkdf;
use p256::ecdh::diffie_hellman;
use p256::ecdsa::signature::Signer;
use p256::ecdsa::{Signature, SigningKey, VerifyingKey};
use p256::{PublicKey, SecretKey};
use rand_core::RngCore;
use sha2::{Digest, Sha256};
use std::cell::RefCell;
use std::collections::{HashMap, VecDeque};
use std::rc::Rc;
use std::time::Duration;
use anyhow::anyhow;
use crate::wifi_manager::WIFI_MANAGER;

#[derive(Debug, Clone)]
pub struct TickUpdate {
    pub d0_pressed: bool,
    pub d1_pressed: bool,
    pub d2_pressed: bool,
    pub qr_data: Option<Vec<u8>>,
    pub since_last: Duration,
}

pub struct LockCtx {
    nvs: EspNvs<NvsDefault>,
    pub(crate) lock_secret_key: Option<SecretKey>,
    pub(crate) lock_public_key: Option<PublicKey>,
    pub(crate) contract: Option<InternalContract>,
    is_locked: bool,
    pub(crate) session_token: String,
    pub(crate) mqtt_password: String,
    servo: Servo<'static>,
    dirty: bool,

    time_in_ticks: u64,
    configuration: InternalConfig,
    pub(crate) firmware_manager: FirmwareManager,

    schedule_next_update: Option<u64>,
    mqtt_service: Option<Rc<RefCell<MqttService>>>,
    pub active_screen_idx: usize,

    login_cryptogram: Option<Box<String>>,
    login_cryptogram_nonce: Option<Box<String>>,
}

impl LockCtx {
    pub fn new(
        nvs: EspNvs<NvsDefault>,
        servo: Servo<'static>,
    ) -> Self {
        let mut lck = LockCtx {
            nvs,
            lock_secret_key: None,
            lock_public_key: None,
            contract: None,
            is_locked: false,
            session_token: String::new(),
            mqtt_password: String::new(),
            servo,
            dirty: false,
            time_in_ticks: 0,
            configuration: InternalConfig::default(),
            firmware_manager: FirmwareManager::new(),
            schedule_next_update: None,
            mqtt_service: None,
            active_screen_idx: 0,
            login_cryptogram: None,
            login_cryptogram_nonce: None,
        };

        lck.session_token = lck.load_or_create_session_id();
        lck.mqtt_password = lck.load_or_create_mqtt_password();
        let secret_key = lck.load_or_create_key();
        let my_public = PublicKey::from_secret_scalar(&secret_key.to_nonzero_scalar());

        lck.firmware_manager
            .set_session_token(lck.session_token.clone());

        lck.lock_secret_key = Some(secret_key);
        lck.lock_public_key = Some(my_public);

        let config = lck.get_configuration_or_default();
        lck.configuration = config;

        log::info!("Loaded configuration {:?}", lck.configuration);

        log::info!("Seeing if we have previous state...");

        let maybe_state = lck.read_contract();
        if let Ok(Some(state)) = maybe_state {
            log::info!("Restoring contract state.");
            lck.contract = Some(state.internal_contract);
            log::info!("Contract {:?}", lck.contract);
            if state.is_locked {
                lck.lock_without_update();
            }

            // Start on the lock screen
            lck.active_screen_idx = 1;
        } else {
            // No state. Set as unlocked.
            log::info!("No previous state.");
            lck.unlock_without_update();

            // Use normal qr screen since we don't have state.
            lck.active_screen_idx = 0;
        }

        // This must come after key loading and contract state restoration
        // because we're announcing to the coordinator with our public key and current contract.
        let manager = WIFI_MANAGER.get().unwrap().lock().unwrap();
        if manager.is_connected() {
            // Start MQTT service
            let mqtt_service = MqttService::new(
                lck.configuration.clone(),
                lck.session_token.clone(),
                lck.mqtt_password.clone(),
            );
            lck.mqtt_service = Some(Rc::new(RefCell::new(mqtt_service)));

            if let Some(mut mqtt_service) = lck.mqtt_service.as_ref().map(|m| m.borrow_mut()) {
                mqtt_service.start_mqtt();
            }

            lck.enqueue_announce_message();
            if let Some(mqtt_service) = lck.mqtt_service.as_ref() {
                lck.firmware_manager.set_mqtt_service(mqtt_service.clone());
            }
            lck.firmware_manager.enqueue_get_latest_firmware();
        }
        drop(manager);

        // Do some sanity checks?
        if let Ok(version) = lck.firmware_manager.get_running_version() {
            log::info!("Firmware version: {}", version);
        }
        lck.firmware_manager
            .sanity_check_or_abort()
            .expect("Sanity check failed");

        // Manually clear the dirty flag because we literally just loaded it.
        lck.dirty = false;
        lck
    }

    pub fn save_configuration(&mut self, config: &InternalConfig) {
        use ciborium::ser::into_writer;

        self.configuration = config.clone();

        let mut buf = Vec::new();
        if into_writer(config, &mut buf).is_ok() {
            if self.nvs.set_blob("config", &buf).is_ok() {
                log::info!("Configuration saved to NVS -> [{:?}]", config);
            }
        }
    }

    pub fn get_configuration_or_default(&mut self) -> InternalConfig {
        use ciborium::de::from_reader;

        let mut buffer: [u8; 1024] = [0; 1024];
        match self.nvs.get_blob("config", &mut buffer) {
            Ok(Some(bytes)) => {
                let len = bytes.len();
                from_reader(&buffer[..len]).unwrap_or_else(|e| {
                    log::error!("Failed to deserialize config data: {}", e);
                    InternalConfig::default()
                })
            }
            Ok(None) => {
                log::info!("No configuration data was found, using default");
                InternalConfig::default()
            }
            Err(_) => InternalConfig::default(),
        }
    }

    fn load_or_create_session_id(&mut self) -> String {
        let key_name = "session_key";
        let mut buffer: [u8; 32] = [0; 32];
        let existing_key: Option<&[u8]> = self
            .nvs
            .get_blob(key_name, &mut buffer)
            .expect("The NVS mechanism should have worked");
        let mut rng = Esp32Rng;

        let session_id: String = if let Some(token_bytes) = existing_key {
            let token = String::from_utf8(token_bytes.to_vec()).unwrap();
            log::info!("Loaded existing session ID: {:?}", token);
            token
        } else {
            log::info!("Generating a new session ID...");
            let mut bytes = [0u8; 5]; // 5 bytes -> ~6 base32 chars
            rng.fill_bytes(&mut bytes);
            let token = BASE32_NOPAD.encode(&bytes)[..6].to_string();
            self.nvs.set_blob(key_name, token.as_bytes()).unwrap();
            token
        };

        session_id
    }

    fn load_or_create_mqtt_password(&mut self) -> String {
        let key_name = "mqtt_password";
        let mut buffer: [u8; 128] = [0; 128];
        let existing_key: Option<&[u8]> = self
            .nvs
            .get_blob(key_name, &mut buffer)
            .expect("The NVS mechanism should have worked");
        let mut rng = Esp32Rng;

        let password: String = if let Some(token_bytes) = existing_key {
            let token = String::from_utf8(token_bytes.to_vec()).unwrap();
            log::info!("Loaded existing MQTT password: {:?}", token);
            token
        } else {
            log::info!("Generating a new MQTT password...");
            let mut bytes = [0u8; 32];
            rng.fill_bytes(&mut bytes);
            let token = BASE32_NOPAD.encode(&bytes)[..].to_string();
            self.nvs.set_blob(key_name, token.as_bytes()).unwrap();
            token
        };

        password
    }

    fn load_or_create_key(&mut self) -> SecretKey {
        let key_name = "ec_private";
        let mut buffer: [u8; 256] = [0; 256];
        let existing_key: Option<&[u8]> = self
            .nvs
            .get_blob(key_name, &mut buffer)
            .expect("The NVS mechanism should have worked");
        let mut rng = Esp32Rng;

        let private_key: SecretKey = if let Some(base64_key) = existing_key {
            log::info!("Loaded existing EC private key.");
            let key_bytes = BASE64.decode(base64_key).unwrap();
            SecretKey::from_slice(&key_bytes).expect("Failed to parse private key")
        } else {
            log::info!("Generating a new EC private key...");
            let sk = SecretKey::random(&mut rng);

            let key_bytes = sk.to_bytes();
            let encoded_key = BASE64.encode(&key_bytes);
            self.nvs.set_blob(key_name, encoded_key.as_bytes()).unwrap();
            log::info!("Saved new EC private key to NVS.");
            sk
        };

        private_key
    }

    /// The primary event loop of the firmware. This is called by `main` in fixed intervals of milliseconds.
    /// If the buttons on the face are pressed they'll be passed in here as part of the TickUpdate.
    pub fn tick(&mut self, update: TickUpdate) {
        // Update time
        self.time_in_ticks += update.since_last.as_millis() as u64;

        // Commands to process
        let mut commands: VecDeque<Vec<u8>> = VecDeque::new();
        // Configuration data we might need to update
        let mut configuration: VecDeque<Vec<u8>> = VecDeque::new();
        // Firmware we might need to apply or challenge to respond to
        let mut firmware: VecDeque<Vec<u8>> = VecDeque::new();

        let mut message_queue =
            if let Some(mut mqtt_service) = self.mqtt_service.as_ref().map(|m| m.borrow_mut()) {
                mqtt_service.get_incoming_messages()
            } else {
                Vec::new()
            };

        while !message_queue.is_empty() {
            let message = message_queue.remove(0);
            match message.topic() {
                SignedMessages => {
                    log::info!("Received Command: {:?}", message);
                    commands.push_back(message.buffer());
                }
                ConfigurationData => {
                    log::info!("Received Configuration: {:?}", message);
                    configuration.push_back(message.buffer());
                }
                TopicType::FirmwareMessage => {
                    // log::info!("Received Firmware info: {:?}", message);
                    firmware.push_back(message.buffer());
                }
                _ => {
                    log::info!("Received Unknown message?!: {:?}", message);
                }
            }
        }

        while !commands.is_empty() {
            let buffer = commands.pop_front().unwrap();
            let verifier = SignedMessageVerifier::new();

            let (min_counter, contract_serial_number) =
                if let Some(internal_contract) = &self.contract {
                    (
                        internal_contract.command_counter,
                        internal_contract.serial_number,
                    )
                } else {
                    (0, 0)
                };

            match verifier.verify(
                buffer,
                &self.get_keyring(),
                min_counter,
                contract_serial_number,
            ) {
                Ok(verified_message) => {
                    let for_acknowledgement = verified_message.clone();

                    match self.process_command(verified_message) {
                        Ok(_) => {
                            match &for_acknowledgement {
                                VerifiedType::UnlockCommand(c) => {
                                    self.increment_command_counter(c.counter);
                                }
                                VerifiedType::LockCommand(c) => {
                                    self.increment_command_counter(c.counter);
                                }
                                VerifiedType::ReleaseCommand(c) => {
                                    self.increment_command_counter(c.counter);
                                }
                                VerifiedType::AbortCommand(c) => {
                                    self.increment_command_counter(c.counter);
                                }
                                _ => {}
                            }
                            self.save_state_if_dirty();

                            let acknowledger = Acknowledger::new();
                            let signing_key = self.get_signing_key();
                            let ack_buffer = acknowledger.build_acknowledgement(
                                for_acknowledgement,
                                &self.session_token,
                                &self.lock_public_key.unwrap(),
                                &signing_key,
                            );
                            let ack_message =
                                SignedMessageTransport::new(ack_buffer, Acknowledgments);
                            self.enqueue_message(ack_message)
                        }
                        Err(e) => {
                            let acknowledger = Acknowledger::new();
                            let signing_key = self.get_signing_key();
                            let ack_buffer = acknowledger.build_error_for_command(
                                for_acknowledgement,
                                &self.session_token,
                                &self.lock_public_key.unwrap(),
                                &signing_key,
                                &e,
                            );
                            let ack_message =
                                SignedMessageTransport::new(ack_buffer, Acknowledgments);
                            self.enqueue_message(ack_message)
                        }
                    }
                }
                Err(verification_error) => {
                    log::info!("Verification error {:?}", verification_error);
                    let acknowledger = Acknowledger::new();
                    let signing_key = self.get_signing_key();
                    let ack_buffer = acknowledger.build_error(
                        verification_error,
                        &self.session_token,
                        &self.lock_public_key.unwrap(),
                        &signing_key,
                    );
                    let ack_message = SignedMessageTransport::new(ack_buffer, Acknowledgments);
                    self.enqueue_message(ack_message)
                }
            }
        }

        while !configuration.is_empty() {
            // Configuration delivered through this channel is considered trusted.
            let configuration = configuration.pop_front().unwrap();
            if let Ok(valid_config) = ConfigVerifier::read_configuration(configuration) {
                // The only way we can reach here is we have a valid MQTT provider and connection. So theoretically
                // we're just adding SafetyKeys and/or enable_reset_command.
                self.save_configuration(&valid_config);
            }
        }

        while !firmware.is_empty() {
            let firmware_data = firmware.pop_front().unwrap();
            self.firmware_manager
                .process_message(firmware_data, self.contract.is_some());
        }

        // Process QR payload if present
        if let Some(qr_data) = &update.qr_data {
            self.process_qr_payload(qr_data);
        }

        // D2 button cycles through screens
        if update.d2_pressed {
            self.active_screen_idx = (self.active_screen_idx + 1) % 4;
            log::info!("Active screen now idx -> {}", self.active_screen_idx);
        }

        // D1 button action
        if update.d1_pressed {
            self.handle_d1_action();
        }

        // If MQTT is running, run its tick to process all message queues.
        // Also schedule next periodic update.
        if let Some(mut mqtt_service) = self.mqtt_service.as_ref().map(|m| m.borrow_mut()) {
            mqtt_service.tick(self.time_in_ticks);
        }

        // We need to check that the MQTT service was started, but we don't try to lock/borrow it.
        // The individual enqueue methods will do that.
        if self.mqtt_service.is_some() {
            if self.schedule_next_update.is_none() {
                log::info!("Scheduling next periodic update for 60 seconds");
                self.schedule_next_update =
                    Some(self.time_in_ticks + Duration::from_secs(60).as_millis() as u64);
            }

            if let Some(scheduled_time) = self.schedule_next_update {
                if scheduled_time < self.time_in_ticks {
                    log::info!("Sending update + rescheduling");
                    self.schedule_next_update =
                        Some(self.time_in_ticks + Duration::from_secs(60).as_millis() as u64);
                    self.enqueue_periodic_update_message(false, false);
                }
            }
        }

        self.save_state_if_dirty();
    } // end tick

    pub fn handle_d1_action(&mut self) {
        match self.active_screen_idx {
            1 => {
                let allowed = match &self.contract {
                    Some(c) => c.temporary_unlock_allowed,
                    None => true,
                };
                if allowed {
                    if self.is_locked() {
                        self.local_unlock();
                    } else {
                        self.local_lock();
                    }
                    self.save_state_if_dirty();
                }
            }
            3 => {
                if self.firmware_manager.is_update_available() {
                    log::info!("User initiated a firmware update via D1");
                    self.firmware_manager.initiate_firmware_update();
                }
            }
            _ => {}
        }
    }

    pub fn process_qr_payload(&mut self, qr_data: &[u8]) {
        // 1. Check if WiFi QR
        if let Ok(maybe_str) = std::str::from_utf8(qr_data) {
            if maybe_str.starts_with("WIFI:") {
                if let Some((ssid, password)) = crate::wifi_util::parse_wifi_qr(maybe_str.to_string()) {
                    let mut manager = crate::wifi_manager::WIFI_MANAGER.get().unwrap().lock().unwrap();
                    manager.ssid = Some(ssid);
                    manager.password = Some(password);
                    let _ = manager.robust_connect();
                    return;
                }
            }
        }

        // 2. Check if configuration
        if let Ok(config) = ConfigVerifier::read_configuration(qr_data.to_vec()) {
            if self.contract.is_none() {
                self.save_configuration(&config);
            } else {
                log::info!("Configuration cannot be changed while under contract.");
            }
            return;
        }

        // 3. Check if signed contract or command
        let verifier = SignedMessageVerifier::new();
        let keys = self.get_keyring();
        let payload = BASE64.decode(qr_data).unwrap_or_else(|_| qr_data.to_vec());
        let (min_counter, contract_serial) = self.contract.as_ref().map(|c| (c.command_counter, c.serial_number)).unwrap_or((0, 0));

        if let Ok(cmd) = verifier.verify(payload, &keys, min_counter, contract_serial) {
            let counter_to_update = match &cmd {
                VerifiedType::UnlockCommand(c) => Some(c.counter),
                VerifiedType::LockCommand(c) => Some(c.counter),
                VerifiedType::ReleaseCommand(c) => Some(c.counter),
                VerifiedType::AbortCommand(c) => Some(c.counter),
                _ => None,
            };
            if let Ok(_) = self.process_command(cmd) {
                if let Some(counter) = counter_to_update {
                    self.increment_command_counter(counter);
                }
                self.save_state_if_dirty();
            }
        }
    }

    pub fn process_command(&mut self, command: VerifiedType) -> Result<bool, String> {
        self.check_permission(&command)?;

        match command {
            VerifiedType::Contract(contract) => {
                if self.contract.is_some() {
                    return Err("Lock already under contract".to_string());
                }
                self.accept_contract(&contract);
                self.contract = Some(contract);
                self.active_screen_idx = 1;
                self.save_state_if_dirty();
                Ok(true)
            }
            VerifiedType::UnlockCommand(_) => {
                self.unlock();
                self.active_screen_idx = 1;
                self.save_state_if_dirty();
                Ok(true)
            }
            VerifiedType::LockCommand(_) => {
                self.lock();
                self.active_screen_idx = 1;
                self.save_state_if_dirty();
                Ok(true)
            }
            VerifiedType::ReleaseCommand(_) => {
                self.end_contract();
                self.active_screen_idx = 0;
                self.save_state_if_dirty();
                Ok(true)
            }
            VerifiedType::AbortCommand(_) => {
                self.end_contract();
                self.active_screen_idx = 0;
                self.save_state_if_dirty();
                Ok(true)
            }
            VerifiedType::ResetCommand(reset) => {
                self.full_reset(&reset);
                self.active_screen_idx = 0;
                self.save_state_if_dirty();
                Ok(true)
            }
        }
    }

    pub fn check_permission(&self, command: &VerifiedType) -> Result<(), String> {
        match command {
            VerifiedType::Contract(_) => Ok(()),
            VerifiedType::UnlockCommand(c) => {
                self.validate_routine_authority(c.authority.as_deref(), |bot| bot.permission.can_unlock)
            }
            VerifiedType::LockCommand(c) => {
                self.validate_routine_authority(c.authority.as_deref(), |bot| bot.permission.can_lock)
            }
            VerifiedType::ReleaseCommand(c) => {
                self.validate_routine_authority(c.authority.as_deref(), |bot| bot.permission.can_release)
            }
            VerifiedType::AbortCommand(c) => self.validate_abort_authority(c.authority.as_deref()),
            VerifiedType::ResetCommand(c) => self.validate_reset_authority(c.authority.as_deref()),
        }
    }

    fn validate_routine_authority<F>(&self, authority: Option<&str>, bot_permission_check: F) -> Result<(), String>
    where
        F: FnOnce(&crate::internal_contract::InternalBot) -> bool,
    {
        match authority {
            None => Ok(()), // Signed by default contract key
            Some(auth_name) => {
                // If it's a safety key, it's not authorized for routine commands
                if let Some(safety_keys) = &self.configuration.safety_keys {
                    if safety_keys.iter().any(|k| k.name == auth_name) {
                        return Err(format!("Safety key '{}' cannot execute routine commands", auth_name));
                    }
                }
                // Check if it's a bot in contract
                if let Some(contract) = &self.contract {
                    if let Some(bot) = contract.bots.iter().find(|b| b.name == auth_name) {
                        if bot_permission_check(bot) {
                            return Ok(());
                        } else {
                            return Err(format!("Bot '{}' lacks permission for this command", auth_name));
                        }
                    }
                }
                Err(format!("Unknown authority '{}'", auth_name))
            }
        }
    }

    fn validate_abort_authority(&self, authority: Option<&str>) -> Result<(), String> {
        match authority {
            None => Ok(()), // Contract holder can abort
            Some(auth_name) => {
                // Safety keys can abort
                if let Some(safety_keys) = &self.configuration.safety_keys {
                    if safety_keys.iter().any(|k| k.name == auth_name) {
                        return Ok(());
                    }
                }
                // Bots cannot abort
                if let Some(contract) = &self.contract {
                    if contract.bots.iter().any(|b| b.name == auth_name) {
                        return Err(format!("Bot '{}' is not permitted to abort contract", auth_name));
                    }
                }
                Err(format!("Authority '{}' is not recognized to abort contract", auth_name))
            }
        }
    }

    fn validate_reset_authority(&self, authority: Option<&str>) -> Result<(), String> {
        match authority {
            Some(auth_name) => {
                // Safety keys can reset
                if let Some(safety_keys) = &self.configuration.safety_keys {
                    if safety_keys.iter().any(|k| k.name == auth_name) {
                        return Ok(());
                    }
                }
                if let Some(contract) = &self.contract {
                    if contract.bots.iter().any(|b| b.name == auth_name) {
                        return Err(format!("Bot '{}' is not permitted to reset device", auth_name));
                    }
                }
                Err(format!("Authority '{}' is not recognized to reset device", auth_name))
            }
            None => Err("ResetCommand requires a recognized safety key authority".to_string()),
        }
    }

    pub fn get_wifi_ssid(&mut self) -> Result<String, String> {
        let mut buffer: [u8; 1024] = [0; 1024];
        if let Ok(maybe_byte_buffer) = self.nvs.get_blob("ssid", &mut buffer) {
            if let Some(ssid) = maybe_byte_buffer {
                return Ok(String::from_utf8(ssid.to_vec()).unwrap());
            } else {
                return Ok("".to_string());
            }
        }
        Err("Failed to get SSID from NVS".to_owned())
    }

    pub(crate) fn increment_command_counter(&mut self, value: u16) {
        let contract = self.contract.take();
        if let Some(mut internal_contract) = contract {
            if internal_contract.command_counter >= value {
                log::warn!(
                    "!!! Refusing to set command counter to {} which is not greater than current counter {}",
                    value,
                    internal_contract.command_counter
                );
                self.contract = Some(internal_contract);
                return;
            }
            internal_contract.command_counter = value;
            self.dirty = true;
            self.contract = Some(internal_contract);
        }
    }

    pub fn local_lock(&mut self) {
        log::info!("Local Locking");
        self.is_locked = true;
        self.servo.close_position();
        self.enqueue_periodic_update_message(false, true);
        self.enqueue_bot_event(EventType::LocalLock);
        self.dirty = true;
    }

    pub fn lock(&mut self) {
        log::info!("Locking");
        self.is_locked = true;
        self.servo.close_position();
        self.enqueue_periodic_update_message(false, false);
        self.enqueue_bot_event(EventType::Lock);
        self.dirty = true;
    }

    pub fn lock_without_update(&mut self) {
        log::info!("Locking");
        self.is_locked = true;
        self.servo.close_position();
        self.dirty = true;
    }

    pub fn local_unlock(&mut self) {
        log::info!("Unlocking");
        self.is_locked = false;
        self.servo.open_position();
        self.enqueue_periodic_update_message(true, false);
        self.enqueue_bot_event(EventType::LocalUnlock);
        self.dirty = true;
    }

    pub fn unlock(&mut self) {
        log::info!("Unlocking");
        self.is_locked = false;
        self.servo.open_position();
        self.enqueue_periodic_update_message(false, false);
        self.enqueue_bot_event(EventType::Unlock);
        self.dirty = true;
    }

    pub fn unlock_without_update(&mut self) {
        log::info!("Unlocking");
        self.is_locked = false;
        self.servo.open_position();
        self.dirty = true;
    }

    pub fn is_locked(&self) -> bool {
        self.is_locked
    }

    pub fn needs_configuration(&self) -> bool {
        !self.configuration.has_token_public_key()
    }

    fn compute_login_credentials(&mut self) {
        if self.needs_configuration() {
            return;
        }
        if self.login_cryptogram.is_some() && self.login_cryptogram_nonce.is_some() {
            return;
        }

        let verifying_key = self.configuration.login_token_public_key();
        let public_key_bytes = verifying_key.to_encoded_point(false);
        let pub_key = PublicKey::from_sec1_bytes(public_key_bytes.as_bytes())
            .expect("Invalid public key bytes");

        let shared_secret = diffie_hellman(
            self.lock_secret_key.as_ref().unwrap().to_nonzero_scalar(),
            pub_key.as_affine(),
        );
        let hkdf = Hkdf::<Sha256>::new(None, shared_secret.raw_secret_bytes());
        let mut okm = [0u8; 32];
        hkdf.expand(b"aes-gcm key", &mut okm)
            .expect("HKDF expand failed");

        let aes_key = Key::<Aes256Gcm>::from_slice(&okm).clone();
        let mut rng = Esp32Rng;
        let nonce = Aes256Gcm::generate_nonce(&mut rng);

        let cipher = Aes256Gcm::new(&aes_key);
        let clear_text = self.session_token.as_bytes();
        let cipher_text = cipher.encrypt(&nonce, clear_text).unwrap();

        self.login_cryptogram = Some(Box::new(BASE64URL.encode(&cipher_text)));
        self.login_cryptogram_nonce = Some(Box::new(BASE64URL.encode(&nonce)));
    }

    pub fn get_lock_url(&mut self) -> Result<String, String> {
        self.compute_login_credentials();

        let coordinator = format!("{}/lock-start", self.configuration.web_uri);
        Ok(format!(
            "{}?cipher={}&nonce={}&session={}",
            coordinator,
            self.login_cryptogram.as_ref().unwrap().as_ref(),
            self.login_cryptogram_nonce.as_ref().unwrap().as_ref(),
            self.session_token
        ))
    }

    pub fn accept_contract(&mut self, _contract: &InternalContract) {
        self.contract = Some(_contract.clone());
        self.enqueue_bot_event(EventType::AcceptContract);
        self.lock();
        self.dirty = true;
    }

    pub(crate) fn save_state_if_dirty(&mut self) {
        use ciborium::ser::into_writer;

        if self.dirty {
            if let Some(internal_contract) = self.contract.as_ref() {
                let save_state = SaveState {
                    internal_contract: internal_contract.clone(),
                    is_locked: self.is_locked,
                };

                let mut bytes = Vec::new();
                if into_writer(&save_state, &mut bytes).is_ok() {
                    if self.nvs.set_blob("contract", &bytes).is_ok() {
                        log::info!("Contract saved to NVS [{}]", bytes.len());
                    } else {
                        log::error!("Failed to save NVS [{}]", bytes.len());
                    }
                }
            } else {
                log::info!("Called save state with no contract. Skipping");
            }
        }

        self.dirty = false;
    }

    pub fn end_contract(&mut self) {
        // NB: Order matters. If we clear the contract before enqueueing bot events
        // we won't have bot records for sending data.
        self.unlock();
        self.enqueue_bot_event(EventType::ReleaseContract);

        self.clear_contract();
    }

    pub fn full_reset(&mut self, reset_command: &InternalResetCommand) {
        if self.configuration.enable_reset_command && reset_command.session() == self.session_token
        {
            unsafe {
                nvs_flash_deinit();
                nvs_flash_erase();
                nvs_flash_init();

                esp_idf_svc::hal::reset::restart();
            }
        }
    }

    pub fn read_contract(&mut self) -> anyhow::Result<Option<SaveState>> {
        use ciborium::de::from_reader;
        let key_name = "contract";

        let required_size = match  self.nvs.blob_len(key_name)? {
            Some(len) => len,
            None => {
                log::info!("No contract data was present");
                return Ok(None);
            }
        };
        let mut buffer = vec![0u8; required_size];

        match self.nvs.get_blob(key_name, &mut buffer) {
            Ok(Some(bytes)) => {
                let len = bytes.len();
                match from_reader(&buffer[..len]) {
                    Ok(state) => Ok(Some(state)),
                    _ => {
                        Err(anyhow!("Contract bytes were set but we couldn't deserialize them"))
                    }
                }
            }
            Ok(None) => {
                log::info!("No contract data was present");
                Ok(None)
            }
            Err(e) => {
                log::error!("Contract was set but failed to read save state: {}", e);
                Err(anyhow!("Contract was set but failed to read save state: {}", e))
            }
        }
    }

    fn clear_contract(&mut self) {
        log::info!("Cleared contract");
        if self.nvs.remove("contract").is_ok() {
            log::info!("Contract removed");
        }
        self.contract = None;
    }

    fn get_signing_key(&self) -> SigningKey {
        let secret = self.lock_secret_key.as_ref().unwrap();

        let cloned_secret = secret.clone();
        let bytes = cloned_secret.to_bytes();
        let key_bytes = bytes.as_slice();
        SigningKey::from_slice(key_bytes).unwrap()
    }

    fn enqueue_announce_message(&self) {
        let current_contract_serial = self.contract.as_ref().map(|c| c.serial_number).unwrap_or(0);
        let started_with_local_contract = self.contract.is_some();

        let mut builder = FlatBufferBuilder::with_capacity(1024);
        let public_key: Vec<u8> = self
            .lock_public_key
            .unwrap()
            .to_sec1_bytes()
            .as_ref()
            .to_vec();
        let session = builder.create_string(&self.session_token);

        let pub_key_holder = builder.create_vector(&public_key);
        let lock_update_event = StartedUpdate::create(
            &mut builder,
            &StartedUpdateArgs {
                public_key: Some(pub_key_holder),
                session: Some(session),
                started_with_local_contract,
                is_locked: self.is_locked,
                current_contract_serial,
            },
        );

        let _payload_type = MessagePayload::StartedUpdate; // Union type
        let _payload_value = lock_update_event.as_union_value();

        builder.finish(lock_update_event, None);
        let buffer = builder.finished_data();

        let table_offset = buffer[0] as usize;
        let vtable_offset = buffer[table_offset] as usize;
        let actual_start = table_offset - vtable_offset;

        let hash = Sha256::digest(&buffer[actual_start..]);

        // UGH. We have to build the whole message over again because of the way
        // Rust implements flatbuffers.
        let mut builder = FlatBufferBuilder::with_capacity(1024);
        let public_key: Vec<u8> = self
            .lock_public_key
            .unwrap()
            .to_sec1_bytes()
            .as_ref()
            .to_vec();
        let session = builder.create_string(&self.session_token);

        let pub_key_holder = builder.create_vector(&public_key);
        let lock_update_event = StartedUpdate::create(
            &mut builder,
            &StartedUpdateArgs {
                public_key: Some(pub_key_holder),
                session: Some(session),
                started_with_local_contract,
                is_locked: self.is_locked,
                current_contract_serial,
            },
        );

        let payload_type = MessagePayload::StartedUpdate; // Union type
        let payload_value = lock_update_event.as_union_value();

        let secret = self.lock_secret_key.as_ref().unwrap();

        let cloned_secret = secret.clone();
        let bytes = cloned_secret.to_bytes();
        let key_bytes = bytes.as_slice();
        let signing_key = SigningKey::from_slice(key_bytes).unwrap();
        let signature: Signature = signing_key.sign(hash.as_slice());

        let sig_bytes = signature.to_bytes();

        let signature_offset = builder.create_vector(sig_bytes.as_slice());
        let signed_message = SignedMessage::create(
            &mut builder,
            &SignedMessageArgs {
                signature: Some(signature_offset),
                payload: Some(payload_value),
                payload_type,
                authority_identifier: None,
            },
        );

        builder.finish(signed_message, None);
        let data = builder.finished_data().to_vec();

        let t = SignedMessageTransport::new(data, SignedMessages);

        self.enqueue_message(t);
    }

    fn enqueue_periodic_update_message(&self, local_unlock: bool, local_lock: bool) {
        let mut builder = FlatBufferBuilder::with_capacity(1024);
        let session = builder.create_string(&self.session_token);

        let current_contract_serial = match &self.contract {
            Some(s) => s.serial_number,
            None => 0,
        };

        // let pub_key_holder = builder.create_vector(&public_key);
        let periodic_update_event = PeriodicUpdate::create(
            &mut builder,
            &PeriodicUpdateArgs {
                session: Some(session),
                is_locked: self.is_locked,
                current_contract_serial,
                local_unlock,
                local_lock,
            },
        );

        let _payload_type = MessagePayload::StartedUpdate; // Union type
        let _payload_value = periodic_update_event.as_union_value();

        builder.finish(periodic_update_event, None);
        let buffer = builder.finished_data();

        let signature = calculate_signature(buffer, &self.get_signing_key());

        let mut builder = FlatBufferBuilder::with_capacity(1024);
        let session = builder.create_string(&self.session_token);

        // let pub_key_holder = builder.create_vector(&public_key);
        let periodic_update_event = PeriodicUpdate::create(
            &mut builder,
            &PeriodicUpdateArgs {
                session: Some(session),
                is_locked: self.is_locked,
                current_contract_serial,
                local_unlock,
                local_lock,
            },
        );

        let payload_type = MessagePayload::PeriodicUpdate; // Union type
        let payload_value = periodic_update_event.as_union_value();

        let signature_offset = builder.create_vector(signature.to_bytes().as_slice());
        let signed_message = SignedMessage::create(
            &mut builder,
            &SignedMessageArgs {
                signature: Some(signature_offset),
                payload: Some(payload_value),
                payload_type,
                authority_identifier: None,
            },
        );

        builder.finish(signed_message, None);
        let data = builder.finished_data().to_vec();
        let t = SignedMessageTransport::new(data, SignedMessages);

        self.enqueue_message(t);
    }

    fn enqueue_bot_event(&self, event_type: EventType) {
        let current_contract_serial = match &self.contract {
            Some(s) => s.serial_number,
            None => 0,
        };

        let mut builder = FlatBufferBuilder::with_capacity(1024);
        let session = builder.create_string(&self.session_token);

        let metadata = CommonMetadata::create(
            &mut builder,
            &CommonMetadataArgs {
                lock_session: Some(session),
                contract_serial_number: current_contract_serial,
                serial_number: 0,
                counter: 0,
            },
        );

        let event = Event::create(
            &mut builder,
            &EventArgs {
                metadata: Some(metadata),
                event_type,
            },
        );

        builder.finish(event, None);
        let buffer = builder.finished_data();

        let signature = calculate_signature(buffer, &self.get_signing_key());

        let mut builder = FlatBufferBuilder::with_capacity(1024);
        let session = builder.create_string(&self.session_token);

        let metadata = CommonMetadata::create(
            &mut builder,
            &CommonMetadataArgs {
                lock_session: Some(session),
                contract_serial_number: current_contract_serial,
                serial_number: 0,
                counter: 0,
            },
        );

        let event = Event::create(
            &mut builder,
            &EventArgs {
                metadata: Some(metadata),
                event_type,
            },
        );

        let signature_offset = builder.create_vector(signature.to_bytes().as_slice());

        let signed_event = SignedEvent::create(
            &mut builder,
            &SignedEventArgs {
                signature: Some(signature_offset),
                payload: Some(event),
            },
        );

        builder.finish(signed_event, None);
        let data = builder.finished_data().to_vec();

        if let Some(contract) = &self.contract {
            for b in &contract.bots {
                if b.permission.receive_events {
                    let t = SignedMessageTransport::new_for_bot(data.clone(), b.name.clone());
                    self.enqueue_message(t);
                }
            }
        }
    }

    pub(crate) fn enqueue_message(&self, message: SignedMessageTransport) {
        if let Some(mqtt_service) = self.mqtt_service.as_ref().map(|m| m.borrow_mut()) {
            mqtt_service.enqueue_message(message);
        }
    }

    pub fn get_keyring(&self) -> HashMap<String, &VerifyingKey> {
        let mut keys: HashMap<String, &VerifyingKey> = HashMap::new();
        if let Some(contract) = &self.contract {
            keys.insert(
                "contract".to_string(),
                contract.public_key.as_ref().unwrap(),
            );

            for bot in &contract.bots {
                keys.insert(bot.name.clone(), bot.public_key.as_ref().unwrap());
            }
        }
        if let Some(safety_keys) = &self.configuration.safety_keys {
            for k in safety_keys {
                keys.insert(k.name.clone(), k.public_key.as_ref().unwrap());
            }
        }
        keys
    }
}
