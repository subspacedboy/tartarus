package club.subjugated.overlord_exe.bots.general

import club.subjugated.fb.bots.BotApiMessage
import club.subjugated.fb.bots.GetContractResponse
import club.subjugated.fb.bots.MessagePayload
import club.subjugated.fb.event.EventType
import club.subjugated.fb.event.SignedEvent
import club.subjugated.overlord_exe.events.SendBotBytes
import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractState
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.util.TimeSource
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.ocpsoft.prettytime.PrettyTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Component
class BotComponent(
    private var contractService: ContractService,
    private var transactionTemplate: TransactionTemplate,
    @Value("\${overlord.mqtt_broker_uri}") private val mqttBrokerUrl: String,
) {
    private val logger: Logger = LoggerFactory.getLogger(BotComponent::class.java)

    lateinit var botExecutorWatchdogService: ScheduledExecutorService

    val pendingRequests = ConcurrentHashMap<Long, CompletableDeferred<BotApiMessage>>()

    var runningBots : HashMap<BotMap, ScheduledExecutorService> = HashMap()
    var handlers : HashMap<BotMap, MessageHandler> = HashMap()
    var botsToClients : HashMap<BotMap, MqttClient> = HashMap<BotMap, MqttClient>()

    fun startBot(botMap : BotMap, handler : MessageHandler) {
        logger.info("Starting bot: ${botMap.internalName}")
        botsToClients[botMap]?.let { existingClient ->
            try {
                if (existingClient.isConnected) {
                    existingClient.disconnectForcibly(1000)
                }
                existingClient.close()
            } catch (e: Exception) {
                logger.warn("Error disconnecting old client for ${botMap.internalName}: $e")
            }
        }
        runningBots[botMap]?.let { existingExecutor ->
            try {
                existingExecutor.shutdownNow()
            } catch (e: Exception) {
                logger.warn("Error shutting down old executor for ${botMap.internalName}: $e")
            }
        }
        var executor = createBotApiExecutor(botMap)
        runningBots.put(botMap, executor)
        handlers.put(botMap, handler)
    }

    @PostConstruct
    fun start() {
        botExecutorWatchdogService = Executors.newSingleThreadScheduledExecutor()
        botExecutorWatchdogService.scheduleAtFixedRate({
            for ((bot, executor) in runningBots) {
                if (executor.isTerminated || executor.isShutdown) {
                    logger.warn("Bot (${bot.name}) executor stopped unexpectedly, restarting...")
                    botsToClients[bot]?.let { oldClient ->
                        try {
                            if (oldClient.isConnected) oldClient.disconnectForcibly(1000)
                            oldClient.close()
                        } catch (e: Exception) {
                            logger.warn("Error closing old client during restart for ${bot.name}: $e")
                        }
                    }
                    runningBots[bot] = createBotApiExecutor(bot)
                }
            }
        }, 1, 5, TimeUnit.SECONDS)

        botExecutorWatchdogService.scheduleAtFixedRate({
            transactionTemplate.execute { status ->
                logger.info("Reviewing live contracts")

                val scope = CoroutineScope(Dispatchers.Default)
                val exceptionHandler = CoroutineExceptionHandler { _, e ->
                    logger.error("Unhandled exception: $e")
                }

                handlers.forEach { botMap, handler ->
                    logger.info("Reviewing ${botMap.internalName}")

                    val contracts = contractService.getLiveContractsForBot(botMap.externalName)
                    scope.async(exceptionHandler) {
                        contracts.forEach { c ->
                            val specificClient = botsToClients[botMap]!!
                            val contractInfo = requestContract(botMap.externalName, c.lockSessionToken!!, c.serialNumber.toUShort(), specificClient)
                            if(contractInfo.state == "ABORTED") {
                                logger.info("Aborted contract: ${c.externalContractName}")
                                c.state = ContractState.ABORTED
                                contractService.save(c)
                            }
                        }
                    }

                    try {
                        handler.reviewContracts(contracts)
                    } catch (ex : Exception) {
                        logger.error("Unhandled exception in reviewing contracts: $ex")
                        ex.printStackTrace()
                        status.setRollbackOnly()
                    }
                }
            }
        }
        , 0, 1, TimeUnit.MINUTES)
    }

    @PreDestroy
    fun stop() {
        botExecutorWatchdogService.shutdown()

        runningBots.forEach { botMap, executor ->
            logger.info("Stopping ${botMap.internalName}")
            executor.shutdown()
            executor.awaitTermination(3, TimeUnit.SECONDS)
        }

        botsToClients.forEach { (botMap, client) ->
            try {
                if (client.isConnected) {
                    client.disconnectForcibly(1000)
                }
                client.close()
            } catch (ex: Exception) {
                logger.warn("Error closing client for ${botMap.internalName}: $ex")
            }
        }
        botsToClients.clear()
        runningBots.clear()
        handlers.clear()
        pendingRequests.clear()

        logger.info("All bots stopped")
    }

    fun createBotApiExecutor(botMap: BotMap): ScheduledExecutorService {
        return Executors.newSingleThreadScheduledExecutor().apply {
            val client = MqttClient(mqttBrokerUrl, botMap.externalName, MemoryPersistence())
            botsToClients.put(botMap, client)

            val options =
                MqttConnectOptions().apply {
                    isCleanSession = false
                    userName = botMap.externalName
                    password = botMap.password.toCharArray()
                    keepAliveInterval = 45
                    isAutomaticReconnect = true
                }

            client.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    logger.info("MQTT connect complete for ${botMap.internalName} / ${botMap.externalName} (reconnect=$reconnect)")
                    try {
                        client.subscribe("bots/inbox_events_${botMap.externalName}")
                        client.subscribe("bots/inbox_api_${botMap.externalName}")
                    } catch (e: Exception) {
                        logger.error("Failed to subscribe on connectComplete for ${botMap.internalName}: $e")
                    }
                }

                override fun connectionLost(cause: Throwable?) {
                    logger.warn("Disconnected (${botMap.internalName} / ${botMap.externalName}): $cause")
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
                                        handleAccept(botMap, signedEvent)
                                    }
                                    EventType.ReleaseContract -> {
                                        handleRelease(botMap, signedEvent)
                                    }
                                    EventType.Lock -> {
                                        handleLock(botMap, signedEvent)
                                    }
                                    EventType.Unlock -> {
                                        handleUnlock(botMap, signedEvent)
                                    }
                                    EventType.EmergencyUnlock -> {
                                        handleEmergencyUnlock(botMap, signedEvent)
                                    }
                                }
                            }

                            if (topic.contains("_api_")) {
                                val botApiMessage = BotApiMessage.getRootAsBotApiMessage(ByteBuffer.wrap(message.payload))
                                val deferred = pendingRequests.remove(botApiMessage.requestId)
                                if (deferred != null) {
                                    deferred.complete(botApiMessage)
                                } else {
                                    logger.warn("Received BotApiMessage for requestId ${botApiMessage.requestId} but no pending request found")
                                }
                            }
                        } catch (ex : Exception) {
                            logger.error("Encountered exception in processing MQTT", ex)
                            ex.printStackTrace()
                            status.setRollbackOnly()
                        }
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            try {
                val connResult = client.connectWithResult(options)
                val sessionPresent = connResult.sessionPresent
                logger.debug("Session present: $sessionPresent for ${botMap.internalName}")

                client.subscribe("bots/inbox_events_${botMap.externalName}")
                client.subscribe("bots/inbox_api_${botMap.externalName}")
            } catch (e: Exception) {
                logger.warn("Initial MQTT connect failed for ${botMap.internalName} (${botMap.externalName}): ${e.message}. Automatic reconnect will continue in background.")
            }
        }
    }

    suspend fun requestContract(botName : String, lockSession: String, serial : UShort, client : MqttClient) : GetContractResponse {
        val handler = CoroutineExceptionHandler { _, exception ->
            logger.error("Request contract coroutine failed for $botName", exception)
        }

        return withContext(Dispatchers.IO + handler) {
            val requestId = Random.nextLong(Long.MAX_VALUE)
            val deferred = CompletableDeferred<BotApiMessage>()
            pendingRequests[requestId] = deferred

            try {
                val requestBody = contractService.makeContractRequest(
                    botName,
                    lockSession,
                    serial,
                    requestId
                )
                if (!client.isConnected) {
                    client.reconnect()
                }
                client.publish("coordinator/inbox", MqttMessage(requestBody))
                val botApiMessage = withTimeoutOrNull(10000) {
                    deferred.await()
                } ?: throw java.util.concurrent.TimeoutException("Timeout waiting for contract response for $botName (requestId=$requestId)")

                val response : GetContractResponse = when(botApiMessage.payloadType){
                    MessagePayload.GetContractResponse -> {
                        val response = GetContractResponse()
                        botApiMessage.payload(response)
                        response
                    }
                    else -> {
                        throw IllegalStateException("Unexpected payload type: ${botApiMessage.payloadType}")
                    }
                }

                response
            } finally {
                pendingRequests.remove(requestId)
            }
        }
    }

    fun handleAccept(botMap: BotMap, signedEvent: SignedEvent) {
        logger.info("Handled accept")

        val scope = CoroutineScope(Dispatchers.Default)
        val exceptionHandler = CoroutineExceptionHandler { _, e ->
            logger.error("Unhandled exception: $e")
        }
        scope.async(exceptionHandler) {
            val e = signedEvent.payload!!
            val commonMetadata = e.metadata!!

            val specificClient = botsToClients[botMap]!!
            val contractInfo = requestContract(botMap.externalName, commonMetadata.lockSession!!, commonMetadata.contractSerialNumber, specificClient)
            logger.debug("Got contract info {}", contractInfo)

            val contract = contractService.getOrCreateContract(botMap.externalName, commonMetadata.lockSession!!, commonMetadata.contractSerialNumber.toInt())
            contractService.updateContractWithGetContractResponse(contract, contractInfo)

            val handler = handlers.get(botMap)!!
            handler.handleAccept(contract)
        }
    }

    fun handleRelease(botMap: BotMap, signedEvent: SignedEvent) {
        logger.info("Handle release")
        val e = signedEvent.payload!!
        val commonMetadata = e.metadata!!

        val contract = contractService.markReleased(botMap.externalName, commonMetadata.contractSerialNumber.toInt())

        val handler = handlers.get(botMap)!!
        handler.handleRelease(contract)
    }

    fun handleLock(botMap : BotMap, signedEvent: SignedEvent) {
        logger.info("Handle Lock")
        val e = signedEvent.payload!!
        val commonMetadata = e.metadata!!

        // Lock gets some special behavior because it happens
        // _immediately_ on accept and might trigger an NPE because
        // the database hasn't saved it yet.
        val contract : Contract = retryOnNullPointer {
            println("Trying ${commonMetadata.contractSerialNumber}")
            contractService.getContract(botMap.externalName, commonMetadata.contractSerialNumber.toInt())
        }

        val handler = handlers.get(botMap)!!
        handler.handleLock(contract)
    }

    fun handleUnlock(botMap: BotMap, signedEvent: SignedEvent) {
        logger.info("Handle Unlock")
        val e = signedEvent.payload!!
        val commonMetadata = e.metadata!!

        val contract = contractService.getContract(botMap.externalName, commonMetadata.contractSerialNumber.toInt())

        val handler = handlers.get(botMap)!!
        handler.handleUnlock(contract)
    }

    fun handleEmergencyUnlock(botMap: BotMap, signedEvent: SignedEvent) {
        logger.info("Handle Emergency Unlock")
        val e = signedEvent.payload!!
        val commonMetadata = e.metadata!!

        val contract = contractService.getContract(botMap.externalName, commonMetadata.contractSerialNumber.toInt())

        val handler = handlers.get(botMap)
        handler?.handleEmergencyUnlock(contract)
    }

    @EventListener
    fun handleSendMessage(event: SendBotBytes) {
        val specificClient = this.botsToClients[event.botMap]
        if (specificClient == null) {
            logger.error("No MQTT client found for bot ${event.botMap.internalName}")
            return
        }
        try {
            if (!specificClient.isConnected) {
                logger.warn("Client for ${event.botMap.internalName} is not connected, attempting reconnect before publish")
                specificClient.reconnect()
            }
            specificClient.publish("coordinator/inbox", event.message)
        } catch (e: Exception) {
            logger.error("Failed to publish SendBotBytes for ${event.botMap.internalName}: $e", e)
        }
    }
}

fun <T> retryOnNullPointer(
    maxAttempts: Int = 3,
    delayMillis: Long = 1000,
    block: () -> T
): T {
    var lastError: NullPointerException? = null

    repeat(maxAttempts) {
        try {
            return block()
        } catch (e: NullPointerException) {
            println("Hit exception...")
            lastError = e
            if (it < maxAttempts - 1) Thread.sleep(delayMillis)
        }
    }

    throw lastError ?: NullPointerException("Unknown NPE in retryOnNullPointer")
}