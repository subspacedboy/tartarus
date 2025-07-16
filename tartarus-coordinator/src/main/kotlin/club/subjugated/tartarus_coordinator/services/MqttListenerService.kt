package club.subjugated.tartarus_coordinator.services

import club.subjugated.fb.bots.BotApiMessage
import club.subjugated.fb.message.firmware.FirmwareMessage
import club.subjugated.tartarus_coordinator.api.bots.BotApiController
import club.subjugated.tartarus_coordinator.api.firmware.FirmwareApiController
import club.subjugated.tartarus_coordinator.api.messages.NewLockSessionMessage
import club.subjugated.tartarus_coordinator.events.NewCommandEvent
import club.subjugated.tartarus_coordinator.models.CommandState
import club.subjugated.tartarus_coordinator.util.*
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.ScheduledExecutorService

@Component
class InternalMqttClient {
    private val clientId: String = "internal"
    @Value("\${tartarus.hivemq.local_tcp_port}") val localTcpPort: Long = 0

    private fun brokerUrl() : String {
        return "tcp://localhost:${localTcpPort}"
    }

    @Bean
    fun mqttClient() : MqttClient {
        return MqttClient(brokerUrl(), clientId, null)
    }
}

@Service
@Transactional
@Profile("!cli")
class MqttListenerService(private val transactionManager: PlatformTransactionManager) {
    private val lockUpdateTopic: String = "locks/updates"

    private val transactionTemplate = TransactionTemplate(transactionManager)
    @Autowired lateinit var client: MqttClient

    private val liveClients: Cache<String, String> =
        Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(10_000).build()

    @Autowired lateinit var lockSessionService: LockSessionService
    @Autowired lateinit var commandQueueService: CommandQueueService
    @Autowired lateinit var configurationService: ConfigurationService
    @Autowired lateinit var firmwareService: FirmwareService

    @Autowired lateinit var botApiController: BotApiController
    @Autowired lateinit var firmwareApiController: FirmwareApiController

    @Autowired lateinit var internalMqttSubscriberPassword : String

    private val executorService = Executors.newSingleThreadExecutor()

    private val botExecutorWatchdogService = Executors.newSingleThreadScheduledExecutor()

    @Value("\${tartarus.hivemq.local_tcp_port}") val localTcpPort: Long = 0

    // TODO Remove this duped brokerUrl and consolidate the client code. Main lock updates should also have watchdog
    private fun brokerUrl() : String {
        return "tcp://localhost:${localTcpPort}"
    }

    fun createBotApiExecutor(): ScheduledExecutorService {
        return Executors.newSingleThreadScheduledExecutor().apply {
            val client = MqttClient(brokerUrl(), "bot_processor", null)
            val options =
                MqttConnectOptions().apply {
                    isCleanSession = false
                    userName = "bot_processor"
                    password = internalMqttSubscriberPassword.toCharArray()
                }

            client.connect(options)
            client.subscribe("coordinator/inbox") {topic, message ->
                transactionTemplate.execute {status ->
                    try {
                        val apiMessage = BotApiMessage.getRootAsBotApiMessage(ByteBuffer.wrap(message.payload))
                        println("🧫 Got Bot API message -> ${apiMessage}")

                        val response = botApiController.routeRequest(apiMessage)

                        client.publish("bots/inbox_api_${apiMessage.name!!}", MqttMessage(response.byteBuffer.array()))
                    } catch (ex : Exception) {
                        println("Encountered exception in processing BOT MQTT")
                        ex.printStackTrace()
                        status.setRollbackOnly()
                    }
                }
            }
        }
    }

    @PostConstruct
    fun start() {
        var executor = createBotApiExecutor()

        botExecutorWatchdogService.scheduleAtFixedRate({
            if (botExecutorWatchdogService.isTerminated || botExecutorWatchdogService.isShutdown) {
                println("Bot executor stopped unexpectedly, restarting...")
                executor = createBotApiExecutor()
            }
        }, 1, 5, TimeUnit.SECONDS)


        executorService.submit {
            val options =
                MqttConnectOptions().apply {
                    isCleanSession = false
                    userName = "internal"
                    password = internalMqttSubscriberPassword.toCharArray()
                }

            client.connect(options)

            client.subscribe(lockUpdateTopic) { _, message ->
                transactionTemplate.execute { status ->
                    try {
                        val keyRequirement =
                            findVerificationKeyRequirement(ByteBuffer.wrap(message.payload))
                        val signedMessage =
                            when (keyRequirement) {
                                ValidationKeyRequirement.KeyIsInMessage -> {
                                    signedMessageBytesValidator(ByteBuffer.wrap(message.payload))
                                }
                                is ValidationKeyRequirement.LockSessionKey -> {
                                    val lockSession =
                                        this.lockSessionService.findBySessionToken(
                                            keyRequirement.sessionToken
                                        )
                                    if(lockSession == null) {
                                        signedMessageBytesValidator(ByteBuffer.wrap(message.payload))
                                    } else {
                                        signedMessageBytesValidatorWithExternalKey(
                                            ByteBuffer.wrap(message.payload),
                                            lockSession.decodePublicKey(),
                                        )
                                    }

                                }
                                else -> {
                                    throw IllegalStateException()
                                }
                            }

                        println("✅ Signature is good -> $signedMessage")

                        when (signedMessage) {
                            is ValidatedPayload.StartedUpdatePayload -> {
                                handleStartUpdateEvent(signedMessage)
                            }
                            is ValidatedPayload.PeriodicUpdatePayload -> {
                                handlePeriodicUpdate(signedMessage)
                            }
                            is ValidatedPayload.AcknowledgementPayload -> {
                                handleAcknowledgePayload(signedMessage)
                            }
                            is ValidatedPayload.ErrorPayload -> {
                                handleErrorPayload(signedMessage)
                            }
                            is ValidatedPayload.UnknownPayload -> {
                                println("‼️ Unknown payload received.")
                            }
                            else -> TODO()
                        }
                    } catch (ex: Exception) {
                        println("Encountered exception in processing MQTT")
                        ex.printStackTrace()
                        status.setRollbackOnly()
                    }
                }
            }

            client.subscribe("locks/firmware") {_, message ->
                transactionTemplate.execute { status ->
                    try {
                        val firmwareApiMessage = FirmwareMessage.getRootAsFirmwareMessage(ByteBuffer.wrap(message.payload))
                        println("🧱 Got Firmware API message -> ${firmwareApiMessage}")

                        val response = firmwareApiController.routeRequest(firmwareApiMessage)

                        if(response != null) {
                            client.publish("firmware/${response.sessionToken!!}", MqttMessage(response.byteBuffer.array()))
                        }
                    }
                    catch (ex : Exception) {
                        println("Encountered exception in processing Firmware MQTT")
                        ex.printStackTrace()
                        status.setRollbackOnly()
                    }
                }
            }
        }
    }

    private fun handleStartUpdateEvent(signedMessage: ValidatedPayload.StartedUpdatePayload) {
        val theUpdate = signedMessage.startedUpdate

        val key =
            ByteArray(theUpdate.publicKeyLength) {
                theUpdate.publicKey(it).toByte()
            }
        val sessionToken = theUpdate.session!!

        this.liveClients.put(sessionToken, "locks/$sessionToken")

        val nlsm =
            NewLockSessionMessage(
                publicKey = Base64.getEncoder().encodeToString(key),
                sessionToken = theUpdate.session!!,
                userSessionPublicKey = ""
            )

        val lockSession = lockSessionService.createLockSession(nlsm)
        lockSessionService.saveLockSession(lockSession)

        val queue = lockSession.commandQueue.first()
        val commands = queue.commands
        val pendingCommands = commands.filter { it.state == CommandState.PENDING }

        for (command in pendingCommands) {
            println("📤 Transmitting command ${command} -> ${sessionToken}")
            client.publish("locks/$sessionToken", MqttMessage(command.body))
        }

        // Send back Configuration data for safety keys.
        val configData = this.configurationService.getConfigurationAsFB()
        println("🧩 Transmitting configuration data -> ${sessionToken}")
        client.publish(
            "configuration/$sessionToken",
            MqttMessage(configData),
        )

        // Also send the firmware challenge :-)
        val firmwareChallenge = firmwareService.generateFirmwareChallenge(sessionToken)
        println("🌮 Transmitting firmware challenge data -> ${sessionToken}")
        client.publish("firmware/${sessionToken}", MqttMessage(firmwareChallenge))
    }

    private fun handlePeriodicUpdate(signedMessage: ValidatedPayload.PeriodicUpdatePayload) {
        val update = signedMessage.periodicUpdate

        println("👀 Periodic update from ${update.session} -> Locked? ${update.isLocked}, Local Lock -> ${update.localLock}, Local Unlock -> ${update.localUnlock}")
        this.lockSessionService.handlePeriodicUpdate(update)
    }

    private fun handleAcknowledgePayload(signedMessage: ValidatedPayload.AcknowledgementPayload) {
        val ack = signedMessage.acknowledgement

        val lockSession =
            this.lockSessionService.findBySessionToken(ack.session!!)
        val commands =
            this.commandQueueService.getCommandBySessionAndSerial(
                lockSession!!,
                ack.serialNumber.toInt(),
            )

        if(commands.size > 1) {
            println("☣️ More than one command matched.")
        }

        println("🥕 Received ack: $ack")
        for(command in commands) {
            println("🥕 Processing ack: ${command.name}")
            this.commandQueueService.acknowledgeCommand(command, ack)
        }
    }

    private fun handleErrorPayload(signedMessage: ValidatedPayload.ErrorPayload) {
        val err = signedMessage.error
        val lockSession =
            this.lockSessionService.findBySessionToken(err.session!!)

        val commands =
            this.commandQueueService.getCommandBySessionAndSerial(
                lockSession!!,
                err.serialNumber.toInt(),
            )

        if(commands.size > 1) {
            println("☣️ More than one command matched for error.")
        }

        for(command in commands) {
            this.commandQueueService.errorCommand(command, err.message)
            println("😞 Error received $err [Command ${command.name}")
        }
    }

    fun tokenHasLiveSession(sessionToken: String): Boolean {
        return this.liveClients.getIfPresent(sessionToken) != null
    }

    @EventListener
    fun handleMessageEvent(event: NewCommandEvent) {
        println("MQTT service received: ${event}")
        val lockSession = this.lockSessionService.findBySessionToken(event.lockSessionToken)
        val commands = this.commandQueueService.getPendingCommandsForSession(lockSession!!)
        for (command in commands) {
            println("📤 Transmitting command ${command} -> ${event.lockSessionToken}")
            client.publish("locks/${event.lockSessionToken}", MqttMessage(command.body))
        }
    }

    @PreDestroy
    fun stop() {
        botExecutorWatchdogService.shutdown()
        executorService.shutdown()
    }
}
