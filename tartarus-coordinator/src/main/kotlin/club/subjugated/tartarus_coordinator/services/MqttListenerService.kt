package club.subjugated.tartarus_coordinator.services

import club.subjugated.fb.message.firmware.FirmwareMessage
import club.subjugated.tartarus_coordinator.api.firmware.FirmwareApiController
import club.subjugated.tartarus_coordinator.api.messages.NewLockSessionMessage
import club.subjugated.tartarus_coordinator.events.NewCommandEvent
import club.subjugated.tartarus_coordinator.models.CommandState
import club.subjugated.tartarus_coordinator.storage.ProvisionedHardwareUnitRepository
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

@Component
class InternalMqttClient {
    private val clientId: String = "internal"
    @Value("\${tartarus.hivemq.local_tcp_port}") val localTcpPort: Long = 0

    private fun brokerUrl(): String {
        return "tcp://localhost:${localTcpPort}"
    }

    @Bean
    fun mqttClient(): MqttClient {
        return MqttClient(brokerUrl(), clientId, null)
    }
}

@Service
@Transactional
@Profile("!cli")
class MqttListenerService(private val transactionManager: PlatformTransactionManager) {
    /**
     * Subscribes using "+" wildcard to per-lock hierarchical topics:
     * - `locks/+/updates`: State reports, session starts, periodic updates, acks, errors
     * - `locks/+/firmware`: Firmware challenge and integrity check requests
     * - `locks/+/events`: Monotonic contract audit and lifecycle events
     *
     * Architectural Rationale:
     * 1. Least-Privilege Broker ACLs: Locks authenticate with their session token and are
     *    restricted to `locks/<client_id>/#`. They cannot publish to or tamper with other locks'
     *    topics or a shared global queue.
     * 2. Transport-Level Identity: The session token is known immediately from the topic string
     *    before decoding/verifying.
     * 3. Targeted Observability: Operations can subscribe to `locks/<session_token>/#` to inspect a
     *    single lock's full traffic.
     * 4. Scalability: Enables partition-by-device sharding across backend worker instances.
     */
    private val lockUpdateTopic: String = "locks/+/updates"
    private val lockFirmwareTopic: String = "locks/+/firmware"
    private val lockEventsTopic: String = "locks/+/events"

    private val transactionTemplate = TransactionTemplate(transactionManager)
    @Autowired lateinit var client: MqttClient

    private val liveClients: Cache<String, String> =
        Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(10_000).build()

    @Autowired lateinit var lockSessionService: LockSessionService
    @Autowired lateinit var commandQueueService: CommandQueueService
    @Autowired lateinit var configurationService: ConfigurationService
    @Autowired lateinit var firmwareService: FirmwareService
    @Autowired(required = false) var unitRepository: ProvisionedHardwareUnitRepository? = null
    @Autowired lateinit var timeSource: TimeSource

    @Autowired lateinit var firmwareApiController: FirmwareApiController
    @Autowired lateinit var eventService: EventService

    @Autowired lateinit var internalMqttSubscriberPassword: String

    private val executorService = Executors.newSingleThreadExecutor()

    @PostConstruct
    fun start() {
        executorService.submit {
            val options =
                MqttConnectOptions().apply {
                    isCleanSession = false
                    userName = "internal"
                    password = internalMqttSubscriberPassword.toCharArray()
                }

            client.connect(options)

            client.subscribe(lockUpdateTopic) { topic, message ->
                transactionTemplate.execute { status ->
                    try {
                        val sessionToken = topic.removePrefix("locks/").removeSuffix("/updates")
                        val keyRequirement =
                            findVerificationKeyRequirement(ByteBuffer.wrap(message.payload))
                        val signedMessage =
                            when (keyRequirement) {
                                ValidationKeyRequirement.KeyIsInMessage -> {
                                    signedMessageBytesValidator(ByteBuffer.wrap(message.payload))
                                }
                                is ValidationKeyRequirement.LockSessionKey -> {
                                    val lockSession =
                                        this.lockSessionService.findBySessionToken(sessionToken)
                                    if (lockSession == null) {
                                        val unitKey =
                                            unitRepository
                                                ?.findByLastKnownSessionToken(sessionToken)
                                                ?.firstOrNull()
                                                ?.lastKnownLockPubkeyB64
                                                ?: unitRepository
                                                    ?.findByInitialSessionToken(sessionToken)
                                                    ?.firstOrNull()
                                                    ?.lastKnownLockPubkeyB64
                                        if (unitKey != null) {
                                            val keyBytes = Base64.getDecoder().decode(unitKey)
                                            signedMessageBytesValidatorWithExternalKey(
                                                ByteBuffer.wrap(message.payload),
                                                keyBytes,
                                            )
                                        } else {
                                            signedMessageBytesValidator(
                                                ByteBuffer.wrap(message.payload)
                                            )
                                        }
                                    } else {
                                        var validated =
                                            signedMessageBytesValidatorWithExternalKey(
                                                ByteBuffer.wrap(message.payload),
                                                lockSession.decodePublicKey(),
                                            )
                                        if (validated is ValidatedPayload.UnknownPayload) {
                                            val unit =
                                                unitRepository
                                                    ?.findByLastKnownSessionToken(sessionToken)
                                                    ?.firstOrNull()
                                                    ?: unitRepository
                                                        ?.findByInitialSessionToken(sessionToken)
                                                        ?.firstOrNull()
                                            val unitKey = unit?.lastKnownLockPubkeyB64
                                            if (
                                                unitKey != null && unitKey != lockSession.publicKey
                                            ) {
                                                val keyBytes = Base64.getDecoder().decode(unitKey)
                                                val revalidated =
                                                    signedMessageBytesValidatorWithExternalKey(
                                                        ByteBuffer.wrap(message.payload),
                                                        keyBytes,
                                                    )
                                                if (
                                                    revalidated !is ValidatedPayload.UnknownPayload
                                                ) {
                                                    lockSession.publicKey = unitKey
                                                    lockSession.updatedAt = timeSource.nowInUtc()
                                                    lockSessionService.saveLockSession(lockSession)
                                                    validated = revalidated
                                                }
                                            }
                                        }
                                        validated
                                    }
                                }
                                else -> {
                                    throw IllegalStateException()
                                }
                            }

                        if (signedMessage !is ValidatedPayload.UnknownPayload) {
                            println("✅ Validated payload for $sessionToken -> $signedMessage")
                        } else {
                            println(
                                "❌ Signature verification failed or unknown payload for $sessionToken"
                            )
                        }

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

            client.subscribe(lockFirmwareTopic) { topic, message ->
                transactionTemplate.execute { status ->
                    try {
                        val sessionToken = topic.removePrefix("locks/").removeSuffix("/firmware")
                        val firmwareApiMessage =
                            FirmwareMessage.getRootAsFirmwareMessage(
                                ByteBuffer.wrap(message.payload)
                            )
                        println(
                            "🧱 Got Firmware API message on $topic for session $sessionToken -> ${firmwareApiMessage}"
                        )

                        val response = firmwareApiController.routeRequest(firmwareApiMessage)

                        if (response != null) {
                            val targetSession = response.sessionToken ?: sessionToken
                            client.publish(
                                "firmware/$targetSession",
                                MqttMessage(response.byteBuffer.array()),
                            )
                        }
                    } catch (ex: Exception) {
                        println("Encountered exception in processing Firmware MQTT")
                        ex.printStackTrace()
                        status.setRollbackOnly()
                    }
                }
            }

            client.subscribe(lockEventsTopic) { topic, message ->
                transactionTemplate.execute { status ->
                    try {
                        val sessionToken = topic.removePrefix("locks/").removeSuffix("/events")
                        println("📨 Got Lock Event on topic $topic for session $sessionToken")
                        eventService.processIncomingLockEvent(sessionToken, message.payload)
                    } catch (ex: Exception) {
                        println(
                            "Encountered exception in processing Lock Event MQTT: ${ex.message}"
                        )
                        ex.printStackTrace()
                        status.setRollbackOnly()
                    }
                }
            }
        }
    }

    private fun handleStartUpdateEvent(signedMessage: ValidatedPayload.StartedUpdatePayload) {
        val theUpdate = signedMessage.startedUpdate

        val key = ByteArray(theUpdate.publicKeyLength) { theUpdate.publicKey(it).toByte() }
        val sessionToken = theUpdate.session!!

        this.liveClients.put(sessionToken, "locks/$sessionToken")

        val nlsm =
            NewLockSessionMessage(
                publicKey = Base64.getEncoder().encodeToString(key),
                sessionToken = theUpdate.session!!,
                userSessionPublicKey = "",
            )

        val lockSession = lockSessionService.createLockSession(nlsm)
        lockSessionService.saveLockSession(lockSession)

        // Update matching provisioned hardware unit lock pubkey if known
        try {
            val pubKeyB64 = nlsm.publicKey
            val matched =
                (unitRepository?.findByLastKnownSessionToken(sessionToken) ?: emptyList()) +
                    (unitRepository?.findByInitialSessionToken(sessionToken) ?: emptyList())
            for (u in matched.distinctBy { it.hardwareId }) {
                u.lastKnownLockPubkeyB64 = pubKeyB64
                u.lastKnownSessionToken = sessionToken
                unitRepository?.save(u)
            }
        } catch (e: Exception) {
            println(
                "⚠️ Warning: could not update provisioned unit for session $sessionToken: ${e.message}"
            )
        }

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
        client.publish("configuration/$sessionToken", MqttMessage(configData))

        // Also send the firmware challenge :-)
        val firmwareChallenge = firmwareService.generateFirmwareChallenge(sessionToken)
        println("🌮 Transmitting firmware challenge data -> ${sessionToken}")
        client.publish("firmware/${sessionToken}", MqttMessage(firmwareChallenge))
    }

    private fun handlePeriodicUpdate(signedMessage: ValidatedPayload.PeriodicUpdatePayload) {
        val update = signedMessage.periodicUpdate

        println(
            "👀 Periodic update from ${update.session} -> Locked? ${update.isLocked}, Local Lock -> ${update.localLock}, Local Unlock -> ${update.localUnlock}, Emergency unlocked? -> ${update.emergencyUnlocked}"
        )
        this.lockSessionService.handlePeriodicUpdate(update)
    }

    private fun handleAcknowledgePayload(signedMessage: ValidatedPayload.AcknowledgementPayload) {
        val ack = signedMessage.acknowledgement

        val lockSession = this.lockSessionService.findBySessionToken(ack.session!!)
        val commands =
            this.commandQueueService.getCommandBySessionAndSerial(
                lockSession!!,
                ack.serialNumber.toInt(),
            )

        if (commands.size > 1) {
            println("☣️ More than one command matched.")
        }

        println("🥕 Received ack: $ack")
        for (command in commands) {
            println("🥕 Processing ack: ${command.name}")
            this.commandQueueService.acknowledgeCommand(command, ack)
        }
    }

    private fun handleErrorPayload(signedMessage: ValidatedPayload.ErrorPayload) {
        val err = signedMessage.error
        val lockSession = this.lockSessionService.findBySessionToken(err.session!!)

        val commands =
            this.commandQueueService.getCommandBySessionAndSerial(
                lockSession!!,
                err.serialNumber.toInt(),
            )

        if (commands.size > 1) {
            println("☣️ More than one command matched for error.")
        }

        for (command in commands) {
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
        executorService.shutdown()
    }
}
