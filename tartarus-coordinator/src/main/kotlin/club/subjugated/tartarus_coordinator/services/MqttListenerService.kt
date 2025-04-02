package club.subjugated.tartarus_coordinator.services

import club.subjugated.fb.message.UpdateType
import club.subjugated.tartarus_coordinator.api.messages.NewLockSessionMessage
import club.subjugated.tartarus_coordinator.util.ValidatedPayload
import club.subjugated.tartarus_coordinator.util.signedMessageBytesValidator
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.Executors

@Service
class MqttListenerService {
    private val brokerUrl: String = "tcp://localhost:1883"
    private val clientId: String = "InternalSubscriber"
    private val topic: String = "locks/updates"

    private val client: MqttClient = MqttClient(brokerUrl, clientId, null)

    @Autowired
    lateinit var lockSessionService: LockSessionService

    @Autowired
    lateinit var contractService: ContractService

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
                val updateMessage = signedMessageBytesValidator(ByteBuffer.wrap(message.payload))
                println("✅ Signature is good -> $updateMessage")

                when(updateMessage) {
                    is ValidatedPayload.LockUpdateEventPayload -> {
                        val theUpdate = updateMessage.lockUpdateEvent
                        val key = ByteArray(theUpdate.publicKeyLength) { theUpdate.publicKey(it).toByte() }
                        val sessionToken = theUpdate.session!!

                        if(theUpdate.thisUpdateType == UpdateType.Started) {
                            val nlsm = NewLockSessionMessage(
                                publicKey = Base64.getEncoder().encodeToString(key),
                                sessionToken = theUpdate.session!!
                            )

                            val lockSession = lockSessionService.createLockSession(nlsm)
                            lockSessionService.saveLockSession(lockSession)

                            //todo - get pending commands, send them
                            client.publish("locks/$sessionToken", MqttMessage(byteArrayOf(0x01, 0x02)))

                            val maybeContract = this.contractService.getPendingOnlineContracts(lockSession)
                            println(maybeContract)
                            if(maybeContract != null) {
                                client.publish("locks/$sessionToken", MqttMessage(maybeContract.body))
                            }

                        }

                    }
                    else -> TODO()
                }
            }

            client.subscribe("device/status") { topic, message ->
                println("👀 Device vanished $message")
            }
        }

    }

    @PreDestroy
    fun stop() {
        executorService.shutdown()
    }
}