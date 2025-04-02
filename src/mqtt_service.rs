use crate::internal_config::InternalConfig;
use embedded_svc::mqtt::client::EventPayload::Received;
use embedded_svc::mqtt::client::{EventPayload, QoS};
use esp_idf_svc::mqtt::client::{
    EspMqttClient, EspMqttConnection, LwtConfiguration, MqttClientConfiguration,
};
use std::collections::VecDeque;
use std::sync::{Arc, Mutex};
use std::thread::JoinHandle;
use std::time::Duration;

pub struct MqttService {
    mqtt_state_machine: Arc<Mutex<MqttStateMachine>>,
    schedule_restart_mqtt: Option<u64>,
    mqtt_thread_1: Option<JoinHandle<()>>,
    mqtt_thread_2: Option<JoinHandle<()>>,
    incoming_messages: Arc<Mutex<VecDeque<SignedMessageTransport>>>,
    outgoing_message: Arc<Mutex<VecDeque<SignedMessageTransport>>>,
    configuration: InternalConfig,
    session_token: String,
}

#[derive(Debug, Clone)]
pub struct SignedMessageTransport {
    buffer: Vec<u8>,
    topic: TopicType,
    bot_name: Option<String>,
}

impl SignedMessageTransport {
    pub fn new(buffer: Vec<u8>, topic: TopicType) -> Self {
        SignedMessageTransport {
            buffer,
            topic,
            bot_name: None,
        }
    }

    pub fn new_for_bot(buffer: Vec<u8>, bot_name: String) -> Self {
        SignedMessageTransport {
            buffer,
            topic: TopicType::BotMessage,
            bot_name: Some(bot_name),
        }
    }

    pub fn buffer(&self) -> Vec<u8> {
        self.buffer.clone()
    }

    pub fn topic(&self) -> TopicType {
        self.topic.clone()
    }

    pub fn bot_name(&self) -> Option<String> {
        self.bot_name.clone()
    }
}

#[derive(Debug, Clone)]
pub enum TopicType {
    SignedMessages,
    ConfigurationData,
    Acknowledgments,
    BotMessage,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum MqttStateMachine {
    NotRunning,
    Running,
    Connected,
    Disconnected,
    Restarting,
}

impl MqttService {
    pub fn new(config: InternalConfig, session_token: String) -> MqttService {
        let mqtt_service = Self {
            mqtt_state_machine: Arc::new(Mutex::new(MqttStateMachine::NotRunning)),
            schedule_restart_mqtt: None,
            mqtt_thread_1: None,
            mqtt_thread_2: None,

            incoming_messages: Arc::new(Mutex::new(Default::default())),
            outgoing_message: Arc::new(Mutex::new(Default::default())),

            configuration: config,
            session_token,
        };

        mqtt_service
    }

    pub fn tick(&mut self, time_in_ticks: u64) {
        let mut do_restart = false;
        if let Ok(mut state) = self.mqtt_state_machine.lock() {
            if *state == MqttStateMachine::Disconnected && self.schedule_restart_mqtt.is_none() {
                *state = MqttStateMachine::Restarting;
                do_restart = true;

                // Check the threads
                log::info!("Scheduling restart of MQTT for 6 seconds");
                self.schedule_restart_mqtt =
                    Some(time_in_ticks + Duration::from_secs(6).as_millis() as u64);
            }
        }

        if do_restart {
            log::info!(
                "Thread 1: {:?}",
                self.mqtt_thread_1.as_ref().unwrap().is_finished()
            );
            log::info!(
                "Thread 2: {:?}",
                self.mqtt_thread_2.as_ref().unwrap().is_finished()
            );

            // Join
            log::info!("Thread 1 joining");
            let t1 = self.mqtt_thread_1.take().unwrap();
            t1.join().unwrap();
            log::info!("Thread 2 joining");
            let t2 = self.mqtt_thread_2.take().unwrap();
            t2.join().unwrap();
        }

        if let Some(scheduled_time) = self.schedule_restart_mqtt {
            if scheduled_time < time_in_ticks {
                log::info!("Restart time: Thread 1: {:?}", self.mqtt_thread_1);
                log::info!("Restart time: Thread 2: {:?}", self.mqtt_thread_2);
                log::info!("Restarting mqtt");
                self.schedule_restart_mqtt = None;
                self.start_mqtt();
                // TODO Normally we'd send a PeriodicUpdate here...
            }
        }
    }

    pub fn get_incoming_messages(&mut self) -> Vec<SignedMessageTransport> {
        self.incoming_messages.lock().unwrap().drain(..).collect()
    }

    pub fn start_mqtt(&mut self) {
        let broker_url = &self.configuration.mqtt_broker_uri; // Replace with your MQTT broker

        let mut config = MqttClientConfiguration::default();
        config.client_id = Some(self.session_token.as_ref());
        config.network_timeout = Duration::from_secs(5);
        // This isn't used. It just needs a value.
        config.password = Some("tartarus");
        config.username = Some(self.session_token.as_ref());
        config.reconnect_timeout = Some(Duration::from_secs(5));
        config.keep_alive_interval = Some(Duration::from_secs(20));

        // Requires -> CONFIG_MBEDTLS_CERTIFICATE_BUNDLE in sdkconfig.defaults
        config.crt_bundle_attach = Some(esp_idf_svc::sys::esp_crt_bundle_attach);

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

        // Need to immediately start pumping the connection for messages, or else subscribe() and publish() below will not work
        // Note that when using the alternative constructor - `EspMqttClient::new_cb` - you don't need to
        // spawn a new thread, as the messages will be pumped with a backpressure into the callback you provide.
        // Yet, you still need to efficiently process each message in the callback without blocking for too long.
        //
        // Note also that if you go to http://tools.emqx.io/ and then connect and send a message to topic
        // "esp-mqtt-demo", the client configured here should receive it.

        let queue_ref = Arc::clone(&self.incoming_messages);
        let out_queue_ref = Arc::clone(&self.outgoing_message);

        let state_machine_ref_1 = Arc::clone(&self.mqtt_state_machine);
        let state_machine_ref_2 = Arc::clone(&self.mqtt_state_machine);

        // Put us to (or back) in running.
        if let Ok(mut state) = self.mqtt_state_machine.lock() {
            *state = MqttStateMachine::Running;
        }

        let session_token = self.session_token.clone();
        let session_token_2 = self.session_token.clone();

        let t1 = std::thread::Builder::new()
            .stack_size(12000)
            .spawn(move || {
                log::info!("MQTT Listening for messages");

                let inbound_queue_name = format!("locks/{session_token_2}");
                let configuration_queue_name = format!("configuration/{session_token_2}");

                while let Ok(event) = connection.next() {
                    match event.payload() {
                        Received { topic, data, .. } => {
                            if let Ok(mut message_queue) = queue_ref.lock() {
                                if let Some(topic) = topic {
                                    match topic {
                                        x if x == inbound_queue_name.as_str() => message_queue
                                            .push_back(SignedMessageTransport::new(
                                                data.to_vec(),
                                                TopicType::SignedMessages,
                                            )),
                                        x if x == configuration_queue_name.as_str() => {
                                            message_queue.push_back(SignedMessageTransport::new(
                                                data.to_vec(),
                                                TopicType::ConfigurationData,
                                            ))
                                        }
                                        _ => {}
                                    }
                                }

                                log::info!("Received MQTT message: {} bytes", data.len());
                            }
                        }
                        EventPayload::Disconnected => {
                            if let Ok(mut state) = state_machine_ref_1.lock() {
                                *state = MqttStateMachine::Disconnected;
                            }
                            log::info!("DISCONNECTED");
                            break;
                        }
                        EventPayload::BeforeConnect => {
                            log::info!("Before connected");
                        }
                        EventPayload::Connected(_) => {
                            log::info!("CONNECTED");
                            if let Ok(mut state) = state_machine_ref_1.lock() {
                                *state = MqttStateMachine::Connected;
                            }
                        }
                        EventPayload::Error(something) => {
                            log::error!("Received some sort of error {}", something);
                        }
                        EventPayload::Published(_) => {}
                        EventPayload::Subscribed(_) => {}
                        other => {
                            log::info!("Something else is happening {:?}", other);
                        }
                    }
                }

                log::info!("Exiting MQTT Connection thread");
            })
            .unwrap();

        let t2 = std::thread::Builder::new()
            .stack_size(12000)
            .spawn(move || {
                log::info!("Subscribe/Publish thread");

                // let session_token = session_token.clone();

                let inbound_queue = format!("locks/{}", &session_token);
                let configuration_queue = format!("configuration/{}", &session_token);
                let queues_to_subscribe_to: Vec<String> = vec![inbound_queue, configuration_queue];
                // let subscription_count = 0_u8;

                'outer: loop {
                    'inner: for q in queues_to_subscribe_to.iter() {
                        if let Err(e) = client.subscribe(&*q, QoS::AtMostOnce) {
                            log::error!("Failed to subscribe to topic : {e}, retrying...");
                            std::thread::sleep(Duration::from_secs(1));
                            continue 'inner;
                        }
                        log::info!("Subscribed -> {q}");
                    }
                    // Just to give a chance of our connection to get even the first published message
                    std::thread::sleep(Duration::from_millis(800));
                    loop {
                        // log::info!("Copying local messages");
                        let mut local_signed_messages: Vec<SignedMessageTransport> = Vec::new();
                        if let Ok(mut message_queue) = out_queue_ref.lock() {
                            while let Some(message) = message_queue.pop_front() {
                                local_signed_messages.push(message);
                            }
                        }
                        // log::info!("Copying local messages finished");

                        while !local_signed_messages.is_empty() {
                            let message = local_signed_messages.remove(0);
                            match message.topic {
                                TopicType::SignedMessages | TopicType::Acknowledgments => {
                                    match client.publish(
                                        "locks/updates",
                                        QoS::AtLeastOnce,
                                        false,
                                        message.buffer.as_ref(),
                                    ) {
                                        Ok(id) => log::info!("Publish successful [{id:?}]"),
                                        Err(e) => {
                                            log::error!("Failed to publish topic: {e}, retrying...")
                                        }
                                    }
                                }
                                TopicType::BotMessage => {
                                    match client.publish(
                                        format!("bots/inbox_{}", message.bot_name.unwrap())
                                            .as_str(),
                                        QoS::AtLeastOnce,
                                        false,
                                        message.buffer.as_ref(),
                                    ) {
                                        Ok(id) => log::info!("Publish successful [{id:?}]"),
                                        Err(e) => {
                                            log::error!("Failed to publish topic: {e}, retrying...")
                                        }
                                    }
                                }
                                _ => {}
                            }
                        }
                        // log::info!("Sending complete");

                        if let Ok(state) = state_machine_ref_2.lock() {
                            // log::info!("State view from publisher: {:?}", state);
                            if *state != MqttStateMachine::Connected {
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

                log::info!("HERE");
                return;
            })
            .unwrap();

        self.mqtt_thread_1 = Some(t1);
        self.mqtt_thread_2 = Some(t2);
    }

    pub fn enqueue_message(&self, message: SignedMessageTransport) {
        if let Ok(mut message_queue) = self.outgoing_message.lock() {
            message_queue.push_back(message);
        }
    }
}
