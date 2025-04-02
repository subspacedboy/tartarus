use std::collections::VecDeque;
use std::sync::{Arc, Mutex};
use crate::boot_screen::BootScreen;
use crate::contract_generated::club::subjugated::fb::message::{MessagePayload, SignedMessage, SignedMessageArgs, StartedUpdate, StartedUpdateArgs};
use crate::internal_contract::{InternalContract, SaveState};
use crate::overlays::{ButtonPressOverlay, WifiOverlay};
use crate::prelude::prelude::{DynOverlay, DynScreen, MyDisplay, MySPI};
use crate::wifi_util::{connect_wifi, parse_wifi_qr};
use crate::Esp32Rng;
use data_encoding::{BASE32_NOPAD, BASE64};
use embedded_graphics_core::pixelcolor::Rgb565;
use embedded_graphics_core::prelude::{DrawTarget, RgbColor};
use esp_idf_hal::gpio::{GpioError, Output, PinDriver};
use esp_idf_svc::nvs::{EspNvs, NvsDefault};
use esp_idf_svc::wifi::{BlockingWifi, EspWifi};
use p256::{PublicKey, SecretKey};
use rand_core::RngCore;
use std::time::Duration;
use base64::Engine;
use base64::prelude::BASE64_URL_SAFE;
use embedded_svc::mqtt::client::EventPayload;
use embedded_svc::mqtt::client::EventPayload::Received;
use esp_idf_svc::mqtt::client::{EspMqttClient, EspMqttConnection, LwtConfiguration, MqttClientConfiguration, QoS};
use flatbuffers::FlatBufferBuilder;
use p256::ecdsa::signature::Signer;
use p256::ecdsa::{Signature, SigningKey, VerifyingKey};
use p256::pkcs8::der::Writer;
use postcard::{from_bytes};
use sha2::{Digest, Sha256};
use crate::acknowledger::Acknowledger;
use crate::internal_config::InternalConfig;
use crate::servo::Servo;
use crate::under_contract_screen::UnderContractScreen;
use crate::verifier::{SignedMessageVerifier, VerifiedType};

#[derive(Debug, Clone)]
pub struct TickUpdate {
    pub d0_pressed : bool,
    pub d1_pressed : bool,
    pub d2_pressed : bool,
    pub qr_data : Option<Vec<u8>>,
    pub since_last: Duration
}

#[derive(Debug, Clone)]
pub struct SignedMessageTransport {
    buffer: Vec<u8>
}

pub struct LockCtx {
    pub(crate) display: MyDisplay<'static>,
    nvs: EspNvs<NvsDefault>,
    current_screen: Option<Box<DynScreen<'static>>>,
    overlays: Vec<Box<DynOverlay<'static>>>,
    wifi: BlockingWifi<EspWifi<'static>>,
    pub(crate) wifi_connected: bool,
    pub(crate) lock_secret_key: Option<SecretKey>,
    pub(crate) lock_public_key: Option<PublicKey>,
    pub(crate) this_update: Option<TickUpdate>,
    pub(crate) contract: Option<InternalContract>,
    is_locked: bool,
    pub(crate) session_token: String,
    incoming_messages: Arc<Mutex<VecDeque<SignedMessageTransport>>>,
    outgoing_message: Arc<Mutex<VecDeque<SignedMessageTransport>>>,
    servo: Servo<'static>,
    dirty: bool,
    restart_mqtt: Arc<Mutex<bool>>,
    schedule_restart_mqtt: Option<u64>,
    time_in_ticks: u64,
    configuration: InternalConfig,
}

impl LockCtx {
    pub fn new(display: MyDisplay<'static>, nvs: EspNvs<NvsDefault>, wifi: BlockingWifi<EspWifi<'static>>, servo: Servo<'static>) -> Self {
        let mut lck = LockCtx {
            display,
            nvs,
            wifi,
            wifi_connected: false,
            overlays: Vec::new(),
            current_screen: None,
            lock_secret_key: None,
            lock_public_key: None,
            this_update: None,
            contract: None,
            is_locked: false,
            session_token: String::new(),
            incoming_messages : Arc::new(Mutex::new(VecDeque::new())),
            outgoing_message : Arc::new(Mutex::new(VecDeque::new())),
            servo,
            dirty: false,
            restart_mqtt: Arc::new(Mutex::new(false)),
            schedule_restart_mqtt: None,
            time_in_ticks: 0,
            configuration: InternalConfig::default(),
        };

        lck.session_token = lck.load_or_create_session_id();
        let secret_key = lck.load_or_create_key();
        let my_public = PublicKey::from_secret_scalar(&secret_key.to_nonzero_scalar());

        lck.lock_secret_key = Some(secret_key);
        lck.lock_public_key = Some(my_public);

        let config = lck.get_configuration_or_default();
        lck.configuration = config;

        // This must come after key loading because we're announcing to the coordinator with our
        // public key.
        if let Ok(connected) = lck.wifi.is_connected() {
            lck.wifi_connected = connected;

            lck.start_mqtt();
            lck.enqueue_announce_message();
        }

        log::info!("Seeing if we have previous state...");
        let maybe_state = lck.read_contract();
        if let Some(state) = maybe_state {
            log::info!("Restoring contract state.");
            lck.contract = Some(state.internal_contract);
            if state.is_locked {
                lck.lock();
            }

            // Back to UnderContract.
            let under_contract: Box<DynScreen<'static>> = Box::new(
                UnderContractScreen::<
                    MySPI<'static>,
                    PinDriver<'static, _, Output>,
                    PinDriver<'static, _, Output>,
                    GpioError
                >::new()
            );
            lck.current_screen = Some(under_contract);
        } else {
            // No state. Set as unlocked.
            log::info!("No previous state.");
            lck.unlock();

            // Use normal boot screen since we don't have state.
            let boot_screen: Box<DynScreen<'static>> = Box::new(
                BootScreen::<
                    MySPI<'static>,
                    PinDriver<'static, _, Output>,
                    PinDriver<'static, _, Output>,
                    GpioError
                >::new()
            );
            lck.current_screen = Some(boot_screen);
        }

        let wifi_overlay: Box<DynOverlay<'static>> = Box::new(
            WifiOverlay::<
                MySPI<'static>,
                PinDriver<'static, _, Output>,
                PinDriver<'static, _, Output>,
                GpioError
            >::new()
        );
        lck.overlays.push(wifi_overlay);

        let button_overlay: Box<DynOverlay<'static>> = Box::new(
            ButtonPressOverlay::<
                MySPI<'static>,
                PinDriver<'static, _, Output>,
                PinDriver<'static, _, Output>,
                GpioError
            >::new()
        );
        lck.overlays.push(button_overlay);
        lck.display.clear(Rgb565::BLACK).unwrap();

        // Manually clear the dirty flag because we literally just loaded it.
        lck.dirty = false;
        lck
    }

    pub fn save_configuration(&mut self, config : &InternalConfig) {
        use postcard::to_vec;

        if let Ok(bytes) = to_vec::<_, 1024>(&config) {
            if let Ok(_) = self.nvs.set_blob("config", bytes.as_slice()) {
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
        let mut buffer : [u8; 32] = [0; 32];
        let existing_key: Option<&[u8]> = self.nvs.get_blob(key_name, &mut buffer).ok().expect("The NVS mechanism should have worked");
        let mut rng = Esp32Rng;

        let session_id : String = if let Some(token_bytes) = existing_key {
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
        let mut buffer : [u8; 256] = [0; 256];
        let existing_key: Option<&[u8]> = self.nvs.get_blob(key_name, &mut buffer).ok().expect("The NVS mechanism should have worked");
        let mut rng = Esp32Rng;

        let private_key : SecretKey = if let Some(base64_key) = existing_key {
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

    pub fn tick(&mut self, update : TickUpdate) -> () {
        // Update time
        self.time_in_ticks += update.since_last.as_millis() as u64;

        // Process message queue
        let mut commands : VecDeque<Vec<u8>> = VecDeque::new();

        if let Ok(mut message_queue) = self.incoming_messages.lock() {
            while message_queue.len() > 0 {
                let current = message_queue.pop_front();
                if let Some(message) = current {
                    log::info!("Received: {:?}", message);
                    commands.push_back(message.buffer);
                }
            }
        }
        while commands.len() > 0 {
            let buffer = commands.pop_front().unwrap();
            let verifier = SignedMessageVerifier::new();
            let mut min_counter = 0_u16;
            let mut contract_serial_number = 0_16;
            let contract_public_key : Option<&VerifyingKey> = if let Some(k) = &self.contract {
                min_counter = k.command_counter;
                contract_serial_number = k.serial_number;
                k.public_key.as_ref()
            } else {
                None
            };

            match verifier.verify(buffer, contract_public_key, min_counter, contract_serial_number) {
                Ok(verified_message) => {
                    let for_acknowledgement = verified_message.clone();
                    self.increment_command_counter();

                    match self.process_command(verified_message) {
                        Ok(_) => {
                            let acknowledger = Acknowledger::new();
                            let signing_key = self.get_signing_key();
                            let ack_buffer = acknowledger.build_acknowledgement(for_acknowledgement, &self.session_token, &self.lock_public_key.unwrap(), &signing_key);
                            let ack_message = SignedMessageTransport {
                                buffer: ack_buffer
                            };
                            self.enqueue_message(ack_message)
                        }
                        Err(e) => {
                            let acknowledger = Acknowledger::new();
                            let signing_key = self.get_signing_key();
                            let ack_buffer = acknowledger.build_error_for_command(for_acknowledgement, &self.session_token, &self.lock_public_key.unwrap(), &signing_key, &e);
                            let ack_message = SignedMessageTransport {
                                buffer: ack_buffer
                            };
                            self.enqueue_message(ack_message)
                        }
                    }
                }
                Err(verification_error) => {
                    log::info!("Verification error {:?}", verification_error);
                    let acknowledger = Acknowledger::new();
                    let signing_key = self.get_signing_key();
                    let ack_buffer = acknowledger.build_error(verification_error, &self.session_token, &self.lock_public_key.unwrap(), &signing_key);
                    let ack_message = SignedMessageTransport {
                        buffer: ack_buffer
                    };
                    self.enqueue_message(ack_message)
                }
            }
        }

        self.this_update = Some(update.clone());
        self.process_updates();

        let mut overlays = core::mem::take(&mut self.overlays);
        for overlay in overlays.iter_mut() {
            overlay.draw_screen(self);
        }
        self.overlays = overlays;

        // TODO: Relocate this block into a struct that can receive QR data
        // and sensibly process it so it's not just sitting here in tick().
        if let Some(incoming_data) = update.qr_data.clone() {
            if let Ok(maybe_string) = String::from_utf8(incoming_data) {
                if maybe_string.starts_with("WIFI:") {
                    let maybe_creds = parse_wifi_qr(maybe_string);
                    if let Some((ssid, password)) = maybe_creds {
                        log::info!("Trying to connect to wifi -> SSID[{}] Password[{}]", ssid, password);

                        let password = "H0la Chic0s";

                        let mut tries = 3;
                        let mut connected = false;
                        while tries > 0 && !connected {
                            if let Ok(_) = connect_wifi(&mut self.wifi, &ssid, &String::from(password)) {
                                log::info!("Connected!");
                                self.wifi_connected = true;
                                connected = true;

                                self.nvs.set_blob("ssid", ssid.as_bytes()).unwrap();
                                self.nvs.set_blob("password", password.as_bytes()).unwrap();
                            } else {
                                log::info!("Failed to connect");
                                tries -=1;
                            }
                        }

                        if !connected {
                            log::info!("Failed to connect and ran out of tries :-(");
                        }

                    }
                }
            }
        }//end of if let Some(incoming_data)

        // Let's see if MQTT is alright.
        if let Ok(needs_restart) = self.restart_mqtt.lock() {
            if *needs_restart && self.schedule_restart_mqtt.is_none() {
                log::info!("Scheduling restart of MQTT for 6 seconds");
                self.schedule_restart_mqtt = Some(self.time_in_ticks + Duration::from_secs(6).as_millis() as u64);
            }
        }

        if let Some(scheduled_time) = self.schedule_restart_mqtt {
            if scheduled_time < self.time_in_ticks {
                log::info!("Restarting mqtt");
                self.schedule_restart_mqtt = None;
                self.start_mqtt();
                self.enqueue_announce_message();
            }
        }

    } // end tick

    // NB: Because of poor choices, this expects LockCtx 'this_update' to have the data
    // to actually process the change.
    pub fn process_updates(&mut self) {
        if let Some(mut current_screen) = self.current_screen.take() {
            let result_from_update = current_screen.on_update(self);

            if let Some(mut new_screen) = result_from_update {
                log::info!("New screen -> {:?}", new_screen.get_id());
                // We state transitioned. So mandatory clear + draw.
                self.display.clear(Rgb565::BLACK).unwrap();
                new_screen.draw_screen(self);
                self.current_screen = Some(new_screen);
            } else {
                if current_screen.needs_redraw() {
                    current_screen.draw_screen(self);
                }
                // Put the current screen back
                self.current_screen = Some(current_screen);
            }
        }
    }

    pub fn process_command(&mut self, command : VerifiedType) -> Result<bool, String> {
        if let Some(mut current_screen) = self.current_screen.take() {
            let result_from_update = current_screen.process_command(self, command);

            match result_from_update {
                Ok(result) => {
                    if let Some(mut new_screen) = result {
                        log::info!("New screen -> {:?}", new_screen.get_id());
                        // We state transitioned. So mandatory clear + draw.
                        self.display.clear(Rgb565::BLACK).unwrap();
                        new_screen.draw_screen(self);
                        self.current_screen = Some(new_screen);
                    } else {
                        if current_screen.needs_redraw() {
                            current_screen.draw_screen(self);
                        }
                        // Put the current screen back
                        self.current_screen = Some(current_screen);
                    }
                }
                Err(error) => {
                    log::error!("Failed to process command: {:?}", error);
                    return Err(error.to_string())
                }
            }
        }

        self.save_state_if_dirty();

        Ok(true)
    }

    fn increment_command_counter(&mut self) {
        let contract = self.contract.take();
        if let Some(mut internal_contract) = contract {
            internal_contract.command_counter += 1;
            self.dirty = true;
            self.contract = Some(internal_contract);
        }
    }

    pub fn lock(&mut self) -> () {
        log::info!("Locking");
        self.is_locked = true;
        self.servo.close_position();
        self.dirty = true;
    }

    pub fn unlock(&mut self) -> () {
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
            let encoded_key = BASE64_URL_SAFE.encode(&compressed_bytes);
            Ok(format!("{}?public={}&session={}", coordinator, encoded_key, self.session_token))
        } else {
            Err("Wasn't able to make the url?!".parse().unwrap())
        }

    }

    pub fn accept_contract(&mut self, _contract : &InternalContract) -> () {
        self.contract = Some(_contract.clone());
        self.lock();
        self.dirty = true;
    }

    fn save_state_if_dirty(&mut self) {
        use postcard::to_vec;
        if self.dirty {
            if let Some(internal_contract) = self.contract.as_ref() {
                // let key = internal_contract.public_key.unwrap();
                // let key_bytes = key.to_encoded_point(true).as_bytes().to_vec();

                let save_state = SaveState {
                    internal_contract: internal_contract.clone(),
                    is_locked: self.is_locked,
                    // verifying_key_bytes: key_bytes,
                };

                if let Ok(bytes) = to_vec::<_, 1024>(&save_state) {
                    if let Ok(_) = self.nvs.set_blob("contract", bytes.as_slice()) {
                        log::info!("Contract saved to NVS");
                    }
                }
            } else {
                log::info!("Called save state with no contract. Skipping");
            }
        }

        self.dirty = false;
    }

    pub fn end_contract(&mut self) -> () {
        self.unlock();
        if let Ok(_) = self.nvs.remove("contract") {
            log::info!("Contract removed");
        }
        self.contract = None;
    }

    pub fn read_contract(&mut self) -> Option<SaveState> {
        let key_name = "contract";
        let mut buffer : [u8; 1024] = [0; 1024];
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

    fn start_mqtt(&self) {
        let broker_url = &self.configuration.mqtt_broker_uri; // Replace with your MQTT broker

        let mut config = MqttClientConfiguration::default();
        config.client_id = Some(self.session_token.as_ref());
        config.network_timeout = Duration::from_secs(5);
        config.password = Some("maybe?");
        config.username = Some(self.session_token.as_ref());
        config.reconnect_timeout = Some(Duration::from_secs(5));

        let lwt_config = LwtConfiguration {
            topic: "devices/status",
            payload: "Vanished".as_bytes(),
            qos: QoS::AtLeastOnce,
            retain: true,
        };
        config.lwt = Some(lwt_config);

        log::info!("Attempting to connect to MQTT");
        let (mut client, mut connection): (EspMqttClient, EspMqttConnection) =
            EspMqttClient::new(broker_url, &config).expect("Failed to connect to MQTT broker");

        // let scope = std::thread::scope(|s| {
        log::info!("About to start the MQTT client");

        // Need to immediately start pumping the connection for messages, or else subscribe() and publish() below will not work
        // Note that when using the alternative constructor - `EspMqttClient::new_cb` - you don't need to
        // spawn a new thread, as the messages will be pumped with a backpressure into the callback you provide.
        // Yet, you still need to efficiently process each message in the callback without blocking for too long.
        //
        // Note also that if you go to http://tools.emqx.io/ and then connect and send a message to topic
        // "esp-mqtt-demo", the client configured here should receive it.

        let queue_ref = Arc::clone(&self.incoming_messages);
        let out_queue_ref = Arc::clone(&self.outgoing_message);
        let restart_mqtt_flag = Arc::clone(&self.restart_mqtt);
        let restart_mqtt_flag_thread_2 = Arc::clone(&self.restart_mqtt);
        // Clear the restart flag
        *restart_mqtt_flag.lock().unwrap() = false;
        let session_token = self.session_token.clone();

        std::thread::Builder::new()
            .stack_size(12000)
            .spawn(move || {
                log::info!("MQTT Listening for messages");

                while let Ok(event) = connection.next() {
                    if let Ok(mut message_queue) = queue_ref.lock() {
                        match event.payload() {
                            Received { data, .. } => {
                                message_queue.push_back(SignedMessageTransport {
                                    buffer: data.to_vec(), // Convert slice to Vec<u8>
                                });
                                log::info!("Received MQTT message: {} bytes", data.len());
                            }
                            EventPayload::Disconnected => {
                                log::info!("DISCONNECTED");
                                break;
                            },
                            _ => {}
                        }
                    }
                }

                // Mark the MQTT as needing a restart. The tick method is responsible for
                // scheduling it to actually happen.
                if let Ok(mut restart) = restart_mqtt_flag.lock() {
                    *restart = true;
                }

                log::info!("Exiting MQTT Connection thread");
            }).unwrap();

        std::thread::Builder::new()
            .stack_size(12000)
            .spawn( move || {
                log::info!("Publish thread");

                // let session_token = session_token.clone();

                'outer: loop {
                    let inbound_queue = format!("locks/{session_token}");
                    if let Err(e) = client.subscribe(&*inbound_queue, QoS::AtMostOnce) {
                        log::error!("Failed to subscribe to topic : {e}, retrying...");
                        std::thread::sleep(Duration::from_secs(2));
                        continue;
                    }

                    // Just to give a chance of our connection to get even the first published message
                    std::thread::sleep(Duration::from_millis(500));
                    loop {

                        if let Ok(mut message_queue) = out_queue_ref.lock() {
                            while let Some(message) = message_queue.pop_front() {
                                match client.publish("locks/updates", QoS::AtLeastOnce, false, message.buffer.as_ref()) {
                                    Ok(id) => log::info!("Publish successful [{id:?}]"),
                                    Err(e) => log::error!("Failed to publish topic: {e}, retrying..."),
                                }
                            }
                        }

                        if let Ok(restart_flag) = restart_mqtt_flag_thread_2.lock() {
                            if *restart_flag {
                                log::info!("Exiting subscribe/publish thread");
                                break 'outer;
                            }
                        }

                        // We use publish and not enqueue because this is already an independent thread.
                        // Enqueue will "wait" for its own (internal) thread while publish will go ahead and use ours.
                        let sleep_secs = 2;
                        std::thread::sleep(Duration::from_secs(sleep_secs));
                    }
                }
            }).unwrap();
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
        let public_key: Vec<u8> = self.lock_public_key.unwrap().to_sec1_bytes().as_ref().to_vec();
        let session = builder.create_string(&self.session_token);

        let pub_key_holder = builder.create_vector(&public_key);
        let lock_update_event = StartedUpdate::create(
            &mut builder,
            &StartedUpdateArgs {
                public_key: Some(pub_key_holder),
                session: Some(session),
                started_with_local_contract: false,
                is_locked: self.is_locked
            },
        );

        let _payload_type = MessagePayload::StartedUpdate; // Union type
        let _payload_value = lock_update_event.as_union_value();

        builder.finish(lock_update_event, None);
        let buffer = builder.finished_data();

        let table_offset = buffer[0] as usize;
        let vtable_offset = buffer[table_offset] as usize;
        let actual_start = table_offset - vtable_offset;

        log::info!("Update buffer w/ vtable ({},{}): {:?}", vtable_offset, table_offset, buffer);
        let hash = Sha256::digest(&buffer[actual_start..]);
        log::info!("Hash {:?}", hash);

        // UGH. We have to build the whole message over again because of the way
        // Rust implements flatbuffers.
        let mut builder = FlatBufferBuilder::with_capacity(1024);
        let public_key: Vec<u8> = self.lock_public_key.unwrap().to_sec1_bytes().as_ref().to_vec();
        let session = builder.create_string(&self.session_token);

        let pub_key_holder = builder.create_vector(&public_key);
        let lock_update_event = StartedUpdate::create(
            &mut builder,
            &StartedUpdateArgs {
                public_key: Some(pub_key_holder),
                session: Some(session),
                started_with_local_contract: false,
                is_locked: self.is_locked
            },
        );

        let payload_type = MessagePayload::StartedUpdate; // Union type
        let payload_value = lock_update_event.as_union_value();

        let secret = self.lock_secret_key.as_ref().unwrap();

        let cloned_secret = secret.clone();
        let bytes = cloned_secret.to_bytes();
        let key_bytes = bytes.as_slice();
        let signing_key = SigningKey::from_slice(key_bytes).unwrap();
        let signature: Signature = signing_key.sign(&hash.as_slice());

        let sig_bytes = signature.to_bytes();

        let signature_offset = builder.create_vector(sig_bytes.as_slice());
        let signed_message = SignedMessage::create(
            &mut builder,
            &SignedMessageArgs {
                signature: Some(signature_offset),
                payload: Some(payload_value),
                payload_type,
            },
        );

        builder.finish(signed_message, None);
        let data = builder.finished_data().to_vec();

        let t = SignedMessageTransport {
            buffer: data
        };

        self.enqueue_message(t);
    }

    fn enqueue_message(&self, message : SignedMessageTransport) {
        if let Ok(mut message_queue) = self.outgoing_message.lock() {
            message_queue.push_back(message);
        }
    }
}