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
use crate::firmware_generated::club;
use crate::firmware_generated::club::subjugated::fb::message::firmware::{
    FirmwareMessage, FirmwareMessageArgs, GetLatestFirmwareRequest, GetLatestFirmwareRequestArgs,
    Version, VersionArgs,
};
use crate::firmware_screen::FirmwareScreen;
use crate::firmware_updater::{read_as_firmware_message, FirmwareManager};
use crate::internal_config::InternalConfig;
use crate::internal_contract::{InternalContract, InternalResetCommand, SaveState};
use crate::internal_firmware::FirmwareMessageType;
use crate::lock_ctx::TopicType::{Acknowledgments, ConfigurationData, SignedMessages};
use crate::lock_state_screen::LockstateScreen;
use crate::mqtt_service::TopicType::FirmwareMessage as TopicFirmwareMessage;
use crate::mqtt_service::{MqttService, SignedMessageTransport, TopicType};
use crate::overlays::{ButtonPressOverlay, WifiOverlay};
use crate::prelude::{DynOverlay, MyDisplay, MySPI};
use crate::qr_screen::QrCodeScreen;
use crate::screen_ids::ScreenId;
use crate::screen_state::ScreenState;
use crate::servo::Servo;
use crate::verifier::{SignedMessageVerifier, VerifiedType};
use crate::wifi_info_screen::WifiInfoScreen;
use crate::wifi_util::connect_wifi;
use crate::Esp32Rng;
use data_encoding::{BASE32_NOPAD, BASE64, BASE64URL};
use embedded_graphics_core::pixelcolor::Rgb565;
use embedded_graphics_core::prelude::{DrawTarget, RgbColor};
use esp_idf_hal::gpio::{AnyOutputPin, GpioError};
use esp_idf_svc::nvs::{EspNvs, NvsDefault};
use esp_idf_svc::sys::{nvs_flash_deinit, nvs_flash_erase, nvs_flash_init};
use esp_idf_svc::wifi::{BlockingWifi, EspWifi};
use flatbuffers::FlatBufferBuilder;
use p256::ecdsa::signature::Signer;
use p256::ecdsa::{Signature, SigningKey, VerifyingKey};
use p256::{PublicKey, SecretKey};
use postcard::from_bytes;
use rand_core::RngCore;
use sha2::{Digest, Sha256};
use std::cell::RefCell;
use std::collections::{HashMap, VecDeque};
use std::rc::Rc;
use std::sync::{Arc, Mutex};
use std::time::Duration;

#[derive(Debug, Clone)]
pub struct TickUpdate {
    pub d0_pressed: bool,
    pub d1_pressed: bool,
    pub d2_pressed: bool,
    pub qr_data: Option<Vec<u8>>,
    pub since_last: Duration,
}

pub struct LockCtx {
    pub(crate) display: MyDisplay<'static>,
    nvs: EspNvs<NvsDefault>,
    overlays: Vec<Box<DynOverlay<'static>>>,
    pub(crate) wifi: BlockingWifi<EspWifi<'static>>,
    pub(crate) wifi_connected: bool,
    pub(crate) lock_secret_key: Option<SecretKey>,
    pub(crate) lock_public_key: Option<PublicKey>,
    pub(crate) this_update: Option<TickUpdate>,
    pub(crate) contract: Option<InternalContract>,
    is_locked: bool,
    pub(crate) session_token: String,
    servo: Servo<'static>,
    dirty: bool,

    time_in_ticks: u64,
    configuration: InternalConfig,
    pub(crate) firmware_manager: FirmwareManager,

    schedule_next_update: Option<u64>,
    mqtt_service: Option<Rc<RefCell<MqttService>>>,
    active_screen_idx: usize,

    qr_code_screen: Option<Box<QrCodeScreen>>,
    wifi_info_screen: Option<Box<WifiInfoScreen>>,
    lock_state_screen: Option<Box<LockstateScreen>>,
    firmware_screen: Option<Box<FirmwareScreen>>,
}

impl LockCtx {
    pub fn new(
        display: MyDisplay<'static>,
        nvs: EspNvs<NvsDefault>,
        wifi: BlockingWifi<EspWifi<'static>>,
        servo: Servo<'static>,
    ) -> Self {
        let mut lck = LockCtx {
            display,
            nvs,
            wifi,
            wifi_connected: false,
            overlays: Vec::new(),
            qr_code_screen: None,
            lock_secret_key: None,
            lock_public_key: None,
            this_update: None,
            contract: None,
            is_locked: false,
            session_token: String::new(),
            servo,
            dirty: false,
            time_in_ticks: 0,
            configuration: InternalConfig::default(),
            firmware_manager: FirmwareManager::new(),
            schedule_next_update: None,
            mqtt_service: None,
            active_screen_idx: 0,
            wifi_info_screen: None,
            lock_state_screen: None,
            firmware_screen: None,
        };

        lck.session_token = lck.load_or_create_session_id();
        let secret_key = lck.load_or_create_key();
        let my_public = PublicKey::from_secret_scalar(&secret_key.to_nonzero_scalar());

        lck.firmware_manager
            .set_session_token(lck.session_token.clone());

        lck.lock_secret_key = Some(secret_key);
        lck.lock_public_key = Some(my_public);

        let config = lck.get_configuration_or_default();
        lck.configuration = config;

        log::info!("Loaded configuration {:?}", lck.configuration);

        // This must come after key loading because we're announcing to the coordinator with our
        // public key.
        if let Ok(connected) = lck.wifi.is_connected() {
            lck.wifi_connected = connected;

            // Start MQTT service
            let mqtt_service =
                MqttService::new(lck.configuration.clone(), lck.session_token.clone());
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

        log::info!("Seeing if we have previous state...");

        let maybe_state = lck.read_contract();
        if let Some(state) = maybe_state {
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

        let qr_code_screen: Box<QrCodeScreen> = Box::new(QrCodeScreen::new());
        lck.qr_code_screen = Some(qr_code_screen);

        let lock_state_screen: Box<LockstateScreen> = Box::new(LockstateScreen::new());
        lck.lock_state_screen = Some(lock_state_screen);

        let wifi_info_screen: Box<WifiInfoScreen> = Box::new(WifiInfoScreen::new());
        lck.wifi_info_screen = Some(wifi_info_screen);

        let firmware_screen: Box<FirmwareScreen> = Box::new(FirmwareScreen::new());
        lck.firmware_screen = Some(firmware_screen);

        let wifi_overlay: Box<DynOverlay<'static>> = Box::new(WifiOverlay::<
            MySPI<'static>,
            AnyOutputPin,
            AnyOutputPin,
            GpioError,
        >::new());
        lck.overlays.push(wifi_overlay);

        let button_overlay: Box<DynOverlay<'static>> = Box::new(ButtonPressOverlay::<
            MySPI<'static>,
            AnyOutputPin,
            AnyOutputPin,
            GpioError,
        >::new());
        lck.overlays.push(button_overlay);
        lck.display.clear(Rgb565::BLACK).unwrap();

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
        use postcard::to_vec;

        self.configuration = config.clone();

        if let Ok(bytes) = to_vec::<_, 1024>(&config) {
            if self.nvs.set_blob("config", bytes.as_slice()).is_ok() {
                log::info!("Configuration saved to NVS");
            }
        }
    }

    pub fn get_configuration_or_default(&mut self) -> InternalConfig {
        let mut buffer: [u8; 1024] = [0; 1024];
        if let Ok(maybe_byte_buffer) = self.nvs.get_blob("config", &mut buffer) {
            if let Some(_byte_buffer) = maybe_byte_buffer {
                if let Ok(deserialized_config) = from_bytes::<InternalConfig>(&buffer) {
                    deserialized_config
                } else {
                    log::error!("Failed to deserialize config data");
                    InternalConfig::default()
                }
            } else {
                log::info!("No configuration data was found, using default");
                InternalConfig::default()
            }
        } else {
            InternalConfig::default()
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
        let mut screen_changed = false;
        let screen_idx_at_start_of_tick = self.active_screen_idx;

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
                    self.increment_command_counter();

                    match self.process_command(verified_message) {
                        Ok(_) => {
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

        if let Some(mut wifi_screen) = self.wifi_info_screen.take() {
            wifi_screen.on_update(self);
            self.wifi_info_screen = Some(wifi_screen);
        }

        // See if the user has physically pressed some buttons or scanned a QR code.
        self.this_update = Some(update.clone());
        self.process_updates();

        // Update all the overlays.
        let mut overlays = core::mem::take(&mut self.overlays);
        for overlay in overlays.iter_mut() {
            overlay.draw_screen(self);
        }
        self.overlays = overlays;

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

        // D2 to cycle through the screens...
        if update.d2_pressed {
            self.active_screen_idx = (self.active_screen_idx + 1) % 4;
            screen_changed = true;
        }

        if screen_idx_at_start_of_tick != self.active_screen_idx {
            log::info!("Active screen now idx -> {}", self.active_screen_idx);
            screen_changed = true;
        }

        // Explicitly draw a screen.
        match ScreenId::from(self.active_screen_idx) {
            ScreenId::QrCode => {
                if let Some(mut qr_screen) = self.qr_code_screen.take() {
                    if screen_changed || qr_screen.needs_redraw() {
                        qr_screen.draw_screen(self);
                    }
                    self.qr_code_screen = Some(qr_screen);
                }
            }
            ScreenId::LockState => {
                if let Some(mut lock_screen) = self.lock_state_screen.take() {
                    if screen_changed || lock_screen.needs_redraw() {
                        log::info!("Drawing lock screen");
                        lock_screen.draw_screen(self);
                    }
                    self.lock_state_screen = Some(lock_screen);
                }
            }
            ScreenId::WifiInfo => {
                if let Some(mut wifi_screen) = self.wifi_info_screen.take() {
                    if screen_changed || wifi_screen.needs_redraw() {
                        wifi_screen.draw_screen(self);
                    }
                    self.wifi_info_screen = Some(wifi_screen);
                }
            }
            ScreenId::FirmwareInfo => {
                if let Some(mut firmware_screen) = self.firmware_screen.take() {
                    if screen_changed || firmware_screen.needs_redraw() {
                        firmware_screen.draw_screen(self);
                    }
                    self.firmware_screen = Some(firmware_screen);
                }
            }
        }

        if self.qr_code_screen.is_none()
            || self.lock_state_screen.is_none()
            || self.wifi_info_screen.is_none()
            || self.firmware_screen.is_none()
        {
            panic!("One of the screens was taken and not replaced");
        }
    } // end tick

    /// Process updates for the current screen. This is different from process_commands
    /// because we're interacting with the user directly via buttons and it's all they
    /// can see.
    // NB: Because of poor choices, this expects LockCtx 'this_update' to have the data
    // to actually process the change.
    pub fn process_updates(&mut self) {
        match self.active_screen_idx {
            0 => {
                if let Some(mut current_screen) = self.qr_code_screen.take() {
                    let result = current_screen.on_update(self);
                    if let Some(value) = result {
                        self.active_screen_idx = value;
                    }
                    self.qr_code_screen = Some(current_screen);
                }
            }
            1 => {
                if let Some(mut lock_screen) = self.lock_state_screen.take() {
                    let result = lock_screen.on_update(self);
                    if let Some(value) = result {
                        self.active_screen_idx = value;
                    }
                    self.lock_state_screen = Some(lock_screen);
                }
            }
            2 => {
                if let Some(mut wifi_info_screen) = self.wifi_info_screen.take() {
                    let result = wifi_info_screen.on_update(self);
                    if let Some(value) = result {
                        self.active_screen_idx = value;
                    }
                    self.wifi_info_screen = Some(wifi_info_screen);
                }
            }
            3 => {
                if let Some(mut firmware_screen) = self.firmware_screen.take() {
                    let result = firmware_screen.on_update(self);
                    if let Some(value) = result {
                        self.active_screen_idx = value;
                    }
                    self.firmware_screen = Some(firmware_screen);
                }
            }
            _ => {}
        }
    }

    pub fn connect_wifi(&mut self, ssid: &String, password: &String) -> Result<bool, String> {
        log::info!(
            "Trying to connect to wifi -> SSID[{}] Password[{}]",
            ssid,
            password
        );

        let mut tries = 2;
        let mut connected = false;
        while tries > 0 && !connected {
            if connect_wifi(&mut self.wifi, ssid, password).is_ok() {
                log::info!("Connected!");
                self.wifi_connected = true;
                connected = true;

                self.nvs.set_blob("ssid", ssid.as_bytes()).unwrap();
                self.nvs.set_blob("password", password.as_bytes()).unwrap();
            } else {
                log::info!("Failed to connect");
                tries -= 1;
            }
        }

        if !connected {
            log::info!("Failed to connect and ran out of tries :-(");
            Err("Unable to connect".to_string())
        } else {
            Ok(true)
        }
    }

    pub fn process_command(&mut self, command: VerifiedType) -> Result<bool, String> {
        // Update: Thanks to refactoring, all commands are handled by lock screen.
        if let Some(mut lock_screen) = self.lock_state_screen.take() {
            if let Ok(succeeded) = lock_screen.process_command(self, command) {
                if let Some(new_screen) = succeeded {
                    self.active_screen_idx = new_screen;
                }

                self.lock_state_screen = Some(lock_screen);
                self.save_state_if_dirty();
                Ok(true)
            } else {
                Err("Something went wrong".to_string())
            }
        } else {
            Err("lock state screen didn't exist?!".to_string())
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

    fn increment_command_counter(&mut self) {
        let contract = self.contract.take();
        if let Some(mut internal_contract) = contract {
            internal_contract.command_counter += 1;
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
        self.enqueue_periodic_update_message(false, false);
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

    pub fn get_lock_url(&self) -> Result<String, String> {
        // const COORDINATOR: &str = "http://192.168.1.180:4200/lock-start";
        let coordinator = format!("{}/lock-start", self.configuration.web_uri);
        if let Some(pub_key) = self.lock_public_key {
            let compressed_bytes = pub_key.to_sec1_bytes();
            let encoded_key = BASE64URL.encode(&compressed_bytes);
            Ok(format!(
                "{}?public={}&session={}",
                coordinator, encoded_key, self.session_token
            ))
        } else {
            Err("Wasn't able to make the url?!".parse().unwrap())
        }
    }

    pub fn accept_contract(&mut self, _contract: &InternalContract) {
        self.contract = Some(_contract.clone());
        self.enqueue_bot_event(EventType::AcceptContract);
        self.lock();
        self.dirty = true;
    }

    fn save_state_if_dirty(&mut self) {
        use postcard::to_vec;
        if self.dirty {
            if let Some(internal_contract) = self.contract.as_ref() {
                let save_state = SaveState {
                    internal_contract: internal_contract.clone(),
                    is_locked: self.is_locked,
                };

                if let Ok(bytes) = to_vec::<_, 1024>(&save_state) {
                    if self.nvs.set_blob("contract", bytes.as_slice()).is_ok() {
                        log::info!("Contract saved to NVS");
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

    pub fn read_contract(&mut self) -> Option<SaveState> {
        let key_name = "contract";
        let mut buffer: [u8; 1024] = [0; 1024];
        if let Ok(maybe_byte_buffer) = self.nvs.get_blob(key_name, &mut buffer) {
            if let Some(_byte_buffer) = maybe_byte_buffer {
                if let Ok(deserialized_state) = from_bytes(&buffer) {
                    Some(deserialized_state)
                } else {
                    log::error!("Failed to deserialize save state");
                    None
                }
            } else {
                log::info!("No contract data was present");
                None
            }
        } else {
            log::info!("No contract key was set");
            None
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
                started_with_local_contract: false,
                is_locked: self.is_locked,
                current_contract_serial: 0,
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
                started_with_local_contract: false,
                is_locked: self.is_locked,
                current_contract_serial: 0,
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
                let t = SignedMessageTransport::new_for_bot(data.clone(), b.name.clone());
                self.enqueue_message(t);
            }
        }
    }

    pub(crate) fn enqueue_message(&self, message: SignedMessageTransport) {
        if let Some(mut mqtt_service) = self.mqtt_service.as_ref().map(|m| m.borrow_mut()) {
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
