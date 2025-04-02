package club.subjugated.tartarus_coordinator.services

import club.subjugated.tartarus_coordinator.api.messages.NewLockSessionMessage
import club.subjugated.tartarus_coordinator.models.CommandState
import club.subjugated.tartarus_coordinator.util.*
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Service
@Transactional
class MqttListenerService(private val transactionManager: PlatformTransactionManager) {
    private val brokerUrl: String = "tcp://localhost:1883"
    private val clientId: String = "InternalSubscriber"
    private val topic: String = "locks/updates"

    private val transactionTemplate = TransactionTemplate(transactionManager)

    private val client: MqttClient = MqttClient(brokerUrl, clientId, null)

    private val liveClients: Cache<String, String> = Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .maximumSize(10_000)
        .build()

    @Autowired
    lateinit var lockSessionService: LockSessionService
    @Autowired
    lateinit var commandQueueService: CommandQueueService

    private val executorService = Executors.newSingleThreadExecutor()

    @PostConstruct
    fun start() {
        executorService.submit {
                val options = MqttConnectOptions().apply {
                isCleanSession = true
                userName = "internal"
                password = "password".toCharArray()
            }

            client.connect(options)
            client.subscribe(topic) { topic, message ->
                transactionTemplate.execute { status ->
                    try {
                        val keyRequirement = findVerificationKeyRequirement(ByteBuffer.wrap(message.payload))
                        val signedMessage = when (keyRequirement) {
                            ValidationKeyRequirement.KeyIsInMessage -> {
                                signedMessageBytesValidator(ByteBuffer.wrap(message.payload))
                            }
                            is ValidationKeyRequirement.LockSessionKey -> {
                                val lockSession = this.lockSessionService.findBySessionToken(keyRequirement.sessionToken)
                                signedMessageBytesValidatorWithExternalKey(ByteBuffer.wrap(message.payload), lockSession.decodePublicKey())
                            }
                            else -> {
                                throw IllegalStateException()
                            }
                        }

                        println("✅ Signature is good -> $signedMessage")

                        when(signedMessage) {
                            is ValidatedPayload.StartedUpdatePayload -> {
                                val theUpdate = signedMessage.startedUpdate

                                val key = ByteArray(theUpdate.publicKeyLength) { theUpdate.publicKey(it).toByte() }
                                val sessionToken = theUpdate.session!!

                                this.liveClients.put(sessionToken, "locks/$sessionToken")

                                val nlsm = NewLockSessionMessage(
                                    publicKey = Base64.getEncoder().encodeToString(key),
                                    sessionToken = theUpdate.session!!
                                )

                                val lockSession = lockSessionService.createLockSession(nlsm)
                                lockSessionService.saveLockSession(lockSession)

                                //todo - get pending commands, send them
                                val commands = this.commandQueueService.getPendingCommandsForSession(lockSession)
                                if(commands.isNotEmpty()) {
                                    val command = commands.first()

                                    client.publish("locks/$sessionToken", MqttMessage(command.body))
                                }
                            }
                            is ValidatedPayload.AcknowledgementPayload -> {
                                val ack = signedMessage.acknowledgement

                                val lockSession = this.lockSessionService.findBySessionToken(ack.session!!)
                                val command = this.commandQueueService.getCommandBySessionAndSerial(lockSession, ack.serialNumber.toInt())
                                this.commandQueueService.acknowledgeCommand(command)

                                println("🥕 Received ack: $ack")
                            }
                            is ValidatedPayload.ErrorPayload -> {
                                val err = signedMessage.error
                                val lockSession = this.lockSessionService.findBySessionToken(err.session!!)

                                val command = this.commandQueueService.getCommandBySessionAndSerial(lockSession, err.serialNumber.toInt())
                                this.commandQueueService.errorCommand(command, err.message)
                                println("😞 Error received $err")
                            }
                            else -> TODO()
                        }
                    } catch (ex : Exception) {
                        status.setRollbackOnly()
                    }

                }
            }
        }
    }

    fun isSessionLive(sessionToken : String) : Boolean {
        return this.liveClients.getIfPresent(sessionToken) != null
    }

    @EventListener
    fun handleMessageEvent(event: NewCommandEvent) {
        println("MQTT service received: ${event}")
        val lockSession = this.lockSessionService.findBySessionToken(event.lockSessionToken)
        val commands = this.commandQueueService.getPendingCommandsForSession(lockSession)
        if(commands.isNotEmpty()) {
            val command = commands.first()

            client.publish("locks/${event.lockSessionToken}", MqttMessage(command.body))
        }
    }

    @PreDestroy
    fun stop() {
        executorService.shutdown()
    }
}