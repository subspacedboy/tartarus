package club.subjugated.overlord_exe.bots.general

import club.subjugated.fb.bots.BotApiMessage
import club.subjugated.fb.bots.GetContractResponse
import club.subjugated.fb.bots.MessagePayload
import club.subjugated.fb.event.EventType
import club.subjugated.fb.event.SignedEvent
import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.util.TimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

@Component
abstract class GenericBotRoot(
    private var botMapService: BotMapService,
    private var contractService: ContractService,
    private var timeSource: TimeSource,
    private var transactionTemplate: TransactionTemplate,
    private var coordinator: String,
    private var mqttBrokerUrl: String,
) {
    private val logger: Logger = LoggerFactory.getLogger(GenericBotRoot::class.java)

    lateinit var botMap : BotMap
    lateinit var mqttClientRef : MqttClient

    protected val botExecutorWatchdogService = Executors.newSingleThreadScheduledExecutor()

    var responseFuture = CompletableDeferred<BotApiMessage>()

    abstract fun handleAccept(signedEvent: SignedEvent)
    abstract fun handleRelease(signedEvent: SignedEvent)
    abstract fun handleLock(signedEvent: SignedEvent)
    abstract fun handleUnlock(signedEvent: SignedEvent)

    fun createBotApiExecutor(botMap: BotMap): ScheduledExecutorService {
        return Executors.newSingleThreadScheduledExecutor().apply {
            val client = MqttClient(mqttBrokerUrl, botMap.externalName, null)
            mqttClientRef = client

            val options =
                MqttConnectOptions().apply {
                    isCleanSession = false
                    userName = botMap.externalName
                    password = botMap.password.toCharArray()
                    keepAliveInterval = 45
                    isAutomaticReconnect = true
                }

            client.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    logger.warn("Disconnected: $cause")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    logger.debug("\uD83D\uDCE8 Received on $topic: ${message!!.id}")

                    transactionTemplate.execute { status ->
                        try {
                            if (topic!!.contains("_events_")) {
                                val signedEvent = SignedEvent.getRootAsSignedEvent(ByteBuffer.wrap(message.payload))

                                val e = signedEvent.payload!!
                                val commonMetadata = e.metadata!!
                                logger.debug("Event -> ${commonMetadata.lockSession} ${commonMetadata.contractSerialNumber}")

                                when (e.eventType) {
                                    EventType.AcceptContract -> {
                                        handleAccept(signedEvent)
                                    }
                                    EventType.ReleaseContract -> {
                                        handleRelease(signedEvent)
                                    }
                                    EventType.Lock -> {
                                        handleLock(signedEvent)
                                    }
                                    EventType.Unlock -> {
                                        handleUnlock(signedEvent)
                                    }
                                }
                            }

                            if (topic.contains("_api_")) {
                                val botApiMessage = BotApiMessage.getRootAsBotApiMessage(ByteBuffer.wrap(message.payload))
                                responseFuture.complete(botApiMessage)
                            }
                        } catch (ex : Exception) {
                            logger.error("Encountered exception in processing MQTT")
                            ex.printStackTrace()
                            status.setRollbackOnly()
                        }
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            client.connectWithResult(options).also { connResult ->
                val sessionPresent = connResult.sessionPresent
                logger.debug("Session present: $sessionPresent")

                // Only subscribe if there's no session already
                if (!sessionPresent) {
                    client.subscribe("bots/inbox_events_${botMap.externalName}")
                    client.subscribe("bots/inbox_api_${botMap.externalName}")
                }
            }
        }
    }

    suspend fun requestContract(botName : String, lockSession: String, serial : UShort, client : MqttClient) : GetContractResponse {
        return withContext(Dispatchers.IO) {
            responseFuture = CompletableDeferred()
            val requestBody = contractService.makeContractRequest(
                botName,
                lockSession,
                serial
            )
            client.publish("coordinator/inbox", MqttMessage(requestBody))
            val botApiMessage = responseFuture.await()

            val response : GetContractResponse = when(botApiMessage.payloadType){
                MessagePayload.GetContractResponse -> {
                    val response = GetContractResponse()
                    botApiMessage.payload(response)
                    response
                }
                else -> {
                    throw IllegalStateException()
                }
            }

            response
        }
    }

    suspend fun addMessage(botName : String, contractName : String, message : String, client : MqttClient) : BotApiMessage {
        return withContext(Dispatchers.IO) {
            responseFuture = CompletableDeferred()
            val requestBody = contractService.makeAddMessageToContractMessage(
                botName,
                contractName,
                message
            )
            client.publish("coordinator/inbox", MqttMessage(requestBody))
            responseFuture.await()
        }
    }

    fun releaseContract(contract: Contract) {
        val releaseCommand = contractService.makeReleaseCommand(botMap.externalName, contract.externalContractName!!, contract.serialNumber, botMap.privateKey!!)
        mqttClientRef.publish("coordinator/inbox", MqttMessage(releaseCommand))
    }

    fun markReleased(serialNumber: Int) {
        contractService.markReleased(botMap.externalName, serialNumber)
    }
}