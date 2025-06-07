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
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.ocpsoft.prettytime.PrettyTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Component
class BotComponent(
    private var contractService: ContractService,
    private var transactionTemplate: TransactionTemplate,
    @Value("\${overlord.mqtt_broker_uri}") private val mqttBrokerUrl: String,
) {
    private val logger: Logger = LoggerFactory.getLogger(BotComponent::class.java)

    lateinit var botExecutorWatchdogService: ScheduledExecutorService

    var responseFuture = CompletableDeferred<BotApiMessage>()

    var runningBots : HashMap<BotMap, ScheduledExecutorService> = HashMap()
    var handlers : HashMap<BotMap, MessageHandler> = HashMap()
    var botsToClients : HashMap<BotMap, MqttClient> = HashMap<BotMap, MqttClient>()

    fun startBot(botMap : BotMap, handler : MessageHandler) {
        logger.info("Starting bot: ${botMap.internalName}")
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

        logger.info("All bots stopped")
    }

    fun createBotApiExecutor(botMap: BotMap): ScheduledExecutorService {
        return Executors.newSingleThreadScheduledExecutor().apply {
            val client = MqttClient(mqttBrokerUrl, botMap.externalName, null)
            botsToClients.put(botMap, client)

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

    @EventListener
    fun handleSendMessage(event: SendBotBytes) {
        val specificClient = this.botsToClients[event.botMap]!!
        specificClient.publish("coordinator/inbox", event.message)
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