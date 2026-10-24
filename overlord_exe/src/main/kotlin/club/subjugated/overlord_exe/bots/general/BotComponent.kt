package club.subjugated.overlord_exe.bots.general

import club.subjugated.fb.event.EventType
import club.subjugated.fb.event.SignedEvent
import club.subjugated.overlord_exe.events.BotRestAction
import club.subjugated.overlord_exe.events.SendBotBytes
import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractState
import club.subjugated.overlord_exe.services.BotContractInfo
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.net.URI
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Serializable
data class BotEventWsPayload(
    val type: String? = null,
    val eventId: Long? = null,
    val botName: String? = null,
    val contractName: String? = null,
    val contractSerial: Int? = null,
    val lockSession: String? = null,
    val counter: Int? = null,
    val eventType: String? = null,
    val correlationId: String? = null,
    val rawSignedEventBase64: String? = null,
)

@Serializable
data class BotCreateContractPayload(
    val shareableToken: String,
    val signedMessageBase64: String,
)

@Serializable
data class BotCreateCommandPayload(
    val contractName: String,
    val shareableToken: String? = null,
    val signedMessageBase64: String,
)

@Serializable
data class BotCreateMessagePayload(
    val message: String,
)

@Component
class BotComponent(
    private var contractService: ContractService,
    private var botMapService: BotMapService,
    private var transactionTemplate: TransactionTemplate,
    private val environment: Environment,
    @Value("\${overlord.coordinator}") private val coordinator: String,
    @Value("\${overlord.ws_uri:ws://localhost:8080/ws/events}") private val wsUri: String,
) {
    private val logger: Logger = LoggerFactory.getLogger(BotComponent::class.java)

    private val jsonParser = Json { ignoreUnknownKeys = true }

    lateinit var botExecutorWatchdogService: ScheduledExecutorService

    var runningBots: ConcurrentHashMap<BotMap, ScheduledExecutorService> = ConcurrentHashMap()
    var handlers: ConcurrentHashMap<BotMap, MessageHandler> = ConcurrentHashMap()

    @Volatile private var activeWebSocket: WebSocket? = null
    @Volatile private var webSocketConnecting: Boolean = false

    fun startBot(botMap: BotMap, handler: MessageHandler) {
        logger.info("Starting bot: ${botMap.internalName} (${botMap.externalName})")
        runningBots[botMap]?.let { existingExecutor ->
            try {
                existingExecutor.shutdownNow()
            } catch (e: Exception) {
                logger.warn("Error shutting down old executor for ${botMap.internalName}: $e")
            }
        }
        val executor = Executors.newSingleThreadScheduledExecutor()
        runningBots[botMap] = executor
        handlers[botMap] = handler

        ensureWebSocketConnected()
    }

    @PostConstruct
    fun start() {
        botExecutorWatchdogService = Executors.newSingleThreadScheduledExecutor()
        botExecutorWatchdogService.scheduleAtFixedRate({
            if (!environment.activeProfiles.contains("test") && handlers.isNotEmpty()) {
                val ws = activeWebSocket
                if (ws == null || ws.isInputClosed || ws.isOutputClosed) {
                    ensureWebSocketConnected()
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

                handlers.forEach { (botMap, handler) ->
                    logger.info("Reviewing ${botMap.internalName}")

                    val contracts = contractService.getLiveContractsForBot(botMap.externalName)
                    scope.async(exceptionHandler) {
                        contracts.forEach { c ->
                            val contractInfo = requestContract(
                                botMap.externalName,
                                c.lockSessionToken!!,
                                c.serialNumber.toUShort(),
                            )
                            if (contractInfo.state == "ABORTED") {
                                logger.info("Aborted contract: ${c.externalContractName}")
                                c.state = ContractState.ABORTED
                                contractService.save(c)
                            }
                        }
                    }

                    try {
                        handler.reviewContracts(contracts)
                    } catch (ex: Exception) {
                        logger.error("Unhandled exception in reviewing contracts: $ex")
                        ex.printStackTrace()
                        status.setRollbackOnly()
                    }
                }
            }
        }, 0, 1, TimeUnit.MINUTES)
    }

    @Synchronized
    private fun ensureWebSocketConnected() {
        if (environment.activeProfiles.contains("test")) {
            return
        }
        val current = activeWebSocket
        if (webSocketConnecting || (current != null && !current.isInputClosed && !current.isOutputClosed)) {
            return
        }
        webSocketConnecting = true
        try {
            val authorJwt = runBlocking { botMapService.getAuthorJwt() }
            val fullUri = URI.create("$wsUri?authorJwt=$authorJwt")
            val httpClient = java.net.http.HttpClient.newHttpClient()
            val messageBuffer = StringBuilder()

            httpClient.newWebSocketBuilder()
                .buildAsync(fullUri, object : WebSocket.Listener {
                    override fun onOpen(webSocket: WebSocket) {
                        logger.info("Connected to Tartarus Coordinator WebSocket at $wsUri")
                        activeWebSocket = webSocket
                        webSocketConnecting = false
                        webSocket.request(1)
                    }

                    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                        messageBuffer.append(data)
                        if (last) {
                            val text = messageBuffer.toString()
                            messageBuffer.setLength(0)
                            handleWebSocketMessage(text)
                        }
                        webSocket.request(1)
                        return null
                    }

                    override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
                        logger.warn("WebSocket closed ($statusCode): $reason")
                        activeWebSocket = null
                        webSocketConnecting = false
                        return null
                    }

                    override fun onError(webSocket: WebSocket, error: Throwable) {
                        logger.warn("WebSocket error: ${error.message}")
                        activeWebSocket = null
                        webSocketConnecting = false
                    }
                })
                .exceptionally { ex ->
                    logger.warn("Failed to connect WebSocket to $wsUri: ${ex.message}")
                    activeWebSocket = null
                    webSocketConnecting = false
                    null
                }
        } catch (e: Exception) {
            logger.warn("Error initiating WebSocket connection: ${e.message}")
            webSocketConnecting = false
        }
    }

    fun handleWebSocketMessage(text: String) {
        transactionTemplate.execute { status ->
            try {
                val wsPayload = jsonParser.decodeFromString<BotEventWsPayload>(text)
                if (wsPayload.type != "bot_event" || wsPayload.rawSignedEventBase64 == null) {
                    return@execute
                }
                val botMap = handlers.keys.find { it.externalName == wsPayload.botName }
                if (botMap == null) {
                    logger.debug("Ignoring bot_event for unmanaged bot ${wsPayload.botName}")
                    return@execute
                }

                val rawBytes = Base64.getDecoder().decode(wsPayload.rawSignedEventBase64)
                val signedEvent = SignedEvent.getRootAsSignedEvent(ByteBuffer.wrap(rawBytes))

                val e = signedEvent.payload!!
                val commonMetadata = e.metadata!!
                logger.debug("Event -> ${commonMetadata.lockSession} ${commonMetadata.contractSerialNumber}")

                when (e.eventType) {
                    EventType.AcceptContract -> handleAccept(botMap, signedEvent)
                    EventType.ReleaseContract -> handleRelease(botMap, signedEvent)
                    EventType.Lock -> handleLock(botMap, signedEvent)
                    EventType.Unlock -> handleUnlock(botMap, signedEvent)
                    EventType.EmergencyUnlock -> handleEmergencyUnlock(botMap, signedEvent)
                }
            } catch (ex: Exception) {
                logger.error("Encountered exception in processing WebSocket bot event", ex)
                ex.printStackTrace()
                status.setRollbackOnly()
            }
        }
    }

    @PreDestroy
    fun stop() {
        botExecutorWatchdogService.shutdown()

        runningBots.forEach { (botMap, executor) ->
            logger.info("Stopping ${botMap.internalName}")
            executor.shutdown()
            executor.awaitTermination(3, TimeUnit.SECONDS)
        }

        try {
            activeWebSocket?.sendClose(WebSocket.NORMAL_CLOSURE, "Shutting down")
        } catch (ex: Exception) {
            logger.warn("Error closing WebSocket: $ex")
        }
        activeWebSocket = null
        runningBots.clear()
        handlers.clear()

        logger.info("All bots stopped")
    }

    suspend fun requestContract(
        botName: String,
        lockSession: String,
        serial: UShort,
    ): BotContractInfo {
        val handler = CoroutineExceptionHandler { _, exception ->
            logger.error("Request contract coroutine failed for $botName", exception)
        }

        return withContext(Dispatchers.IO + handler) {
            val botMap = handlers.keys.find { it.externalName == botName }
            val jwt = if (botMap != null) {
                botMapService.getBotJwt(botMap)
            } else {
                botMapService.getAuthorJwt()
            }

            val client = HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(jsonParser)
                }
            }
            try {
                val response = client.get("$coordinator/bots/$botName/contracts/bySession/$lockSession/$serial") {
                    header(HttpHeaders.Authorization, "Bearer $jwt")
                }
                if (!response.status.isSuccess()) {
                    throw IllegalStateException("Failed to fetch contract for $botName ($lockSession/$serial): HTTP ${response.status} - ${response.bodyAsText()}")
                }
                response.body<BotContractInfo>()
            } finally {
                client.close()
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

            val contractInfo = requestContract(
                botMap.externalName,
                commonMetadata.lockSession!!,
                commonMetadata.contractSerialNumber,
            )
            logger.debug("Got contract info {}", contractInfo)

            val contract = contractService.getOrCreateContract(
                botMap.externalName,
                commonMetadata.lockSession!!,
                commonMetadata.contractSerialNumber.toInt(),
            )
            contractService.updateContractWithGetContractResponse(contract, contractInfo)

            val handler = handlers[botMap]!!
            handler.handleAccept(contract)
        }
    }

    fun handleRelease(botMap: BotMap, signedEvent: SignedEvent) {
        logger.info("Handle release")
        val e = signedEvent.payload!!
        val commonMetadata = e.metadata!!

        val contract = contractService.markReleased(botMap.externalName, commonMetadata.contractSerialNumber.toInt())

        val handler = handlers[botMap]!!
        handler.handleRelease(contract)
    }

    fun handleLock(botMap: BotMap, signedEvent: SignedEvent) {
        logger.info("Handle Lock")
        val e = signedEvent.payload!!
        val commonMetadata = e.metadata!!

        // Lock gets some special behavior because it happens
        // _immediately_ on accept and might trigger an NPE because
        // the database hasn't saved it yet.
        val contract: Contract = retryOnNullPointer {
            println("Trying ${commonMetadata.contractSerialNumber}")
            contractService.getContract(botMap.externalName, commonMetadata.contractSerialNumber.toInt())
        }

        val handler = handlers[botMap]!!
        handler.handleLock(contract)
    }

    fun handleUnlock(botMap: BotMap, signedEvent: SignedEvent) {
        logger.info("Handle Unlock")
        val e = signedEvent.payload!!
        val commonMetadata = e.metadata!!

        val contract = contractService.getContract(botMap.externalName, commonMetadata.contractSerialNumber.toInt())

        val handler = handlers[botMap]!!
        handler.handleUnlock(contract)
    }

    fun handleEmergencyUnlock(botMap: BotMap, signedEvent: SignedEvent) {
        logger.info("Handle Emergency Unlock")
        val e = signedEvent.payload!!
        val commonMetadata = e.metadata!!

        val contract = contractService.getContract(botMap.externalName, commonMetadata.contractSerialNumber.toInt())

        val handler = handlers[botMap]
        handler?.handleEmergencyUnlock(contract)
    }

    @EventListener
    fun handleSendMessage(event: SendBotBytes) {
        if (environment.activeProfiles.contains("test")) {
            return
        }
        val action = event.action

        try {
            val botJwt = botMapService.getBotJwt(event.botMap)
            val botName = event.botMap.externalName

            runBlocking {
                val client = HttpClient(OkHttp) {
                    install(ContentNegotiation) {
                        json(jsonParser)
                    }
                }
                try {
                    val response = when (action) {
                        is BotRestAction.CreateContract -> {
                            client.post("$coordinator/bots/$botName/contracts") {
                                header(HttpHeaders.Authorization, "Bearer $botJwt")
                                contentType(ContentType.Application.Json)
                                setBody(
                                    BotCreateContractPayload(
                                        shareableToken = action.shareableToken,
                                        signedMessageBase64 = action.signedMessageBase64,
                                    )
                                )
                            }
                        }
                        is BotRestAction.CreateCommand -> {
                            client.post("$coordinator/bots/$botName/commands") {
                                header(HttpHeaders.Authorization, "Bearer $botJwt")
                                contentType(ContentType.Application.Json)
                                setBody(
                                    BotCreateCommandPayload(
                                        contractName = action.contractName,
                                        shareableToken = action.shareableToken,
                                        signedMessageBase64 = action.signedMessageBase64,
                                    )
                                )
                            }
                        }
                        is BotRestAction.CreateMessage -> {
                            client.post("$coordinator/bots/$botName/contracts/${action.contractName}/messages") {
                                header(HttpHeaders.Authorization, "Bearer $botJwt")
                                contentType(ContentType.Application.Json)
                                setBody(
                                    BotCreateMessagePayload(
                                        message = action.message,
                                    )
                                )
                            }
                        }
                    }
                    if (!response.status.isSuccess()) {
                        logger.error("Failed REST action $action for ${event.botMap.internalName}: HTTP ${response.status} - ${response.bodyAsText()}")
                    }
                } finally {
                    client.close()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to dispatch SendBotBytes for ${event.botMap.internalName}: $e", e)
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
