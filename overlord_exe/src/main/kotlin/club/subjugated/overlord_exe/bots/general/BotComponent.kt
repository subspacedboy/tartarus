package club.subjugated.overlord_exe.bots.general

import club.subjugated.fb.event.EventType
import club.subjugated.fb.event.SignedEvent
import club.subjugated.overlord_exe.events.BotRestAction
import club.subjugated.overlord_exe.events.SendBotBytes
import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractState
import club.subjugated.overlord_exe.runtime.BotContext
import club.subjugated.overlord_exe.runtime.CoordinatorGateway
import club.subjugated.overlord_exe.runtime.DefaultBotContext
import club.subjugated.overlord_exe.runtime.OverlordBot
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.BotContractInfo
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
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
    val eventId: String? = null,
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

/**
 * Central umbrella host runtime managing bot registration, scoped [BotContext] creation,
 * Author-multiplexed WebSocket event routing (`/ws/events?authorJwt=...`), and isolated per-bot periodic reviews.
 */
@Component
class BotComponent @Autowired constructor(
    private var contractService: ContractService,
    private var botMapService: BotMapService,
    private var transactionTemplate: TransactionTemplate,
    private val environment: Environment,
    private val coordinatorGateway: CoordinatorGateway,
    @Lazy @Autowired(required = false) private val blueSkyService: BlueSkyService? = null,
    @Lazy @Autowired(required = false) private val discoveredBots: List<OverlordBot> = emptyList(),
    @Value("\${overlord.coordinator}") private val coordinator: String,
    @Value("\${overlord.ws_uri:ws://localhost:8080/ws/events}") private val wsUri: String,
) : SmartInitializingSingleton {
    private val logger: Logger = LoggerFactory.getLogger(BotComponent::class.java)

    private val jsonParser = Json { ignoreUnknownKeys = true }

    lateinit var botExecutorWatchdogService: ScheduledExecutorService

    var handlers: ConcurrentHashMap<BotMap, MessageHandler> = ConcurrentHashMap()
    private val contextsByInternalName = ConcurrentHashMap<String, BotContext>()
    private val contextsByBotMap = ConcurrentHashMap<BotMap, BotContext>()

    @Volatile private var activeWebSocket: WebSocket? = null
    @Volatile private var webSocketConnecting: Boolean = false

    fun contextFor(bot: OverlordBot): BotContext {
        return contextFor(bot.descriptor.internalName, bot.descriptor.description)
    }

    fun contextFor(internalName: String, description: String = internalName): BotContext {
        return contextsByInternalName.computeIfAbsent(internalName) {
            val botMap = botMapService.getOrCreateBotMap(internalName, description)
            DefaultBotContext(
                botMapProvider = { botMapService.findBotMap(internalName) ?: botMap },
                botMapService = botMapService,
                contractService = contractService,
                coordinatorGateway = coordinatorGateway,
                blueSkyService = blueSkyService,
            ).also { ctx ->
                contextsByBotMap[botMap] = ctx
            }
        }
    }

    fun startBot(botMap: BotMap, handler: MessageHandler): BotContext {
        logger.info("Starting bot: ${botMap.internalName} (${botMap.externalName})")
        handlers[botMap] = handler
        val ctx = contextsByInternalName.computeIfAbsent(botMap.internalName) {
            DefaultBotContext(
                botMapProvider = { botMapService.findBotMap(botMap.internalName) ?: botMap },
                botMapService = botMapService,
                contractService = contractService,
                coordinatorGateway = coordinatorGateway,
                blueSkyService = blueSkyService,
            )
        }
        contextsByBotMap[botMap] = ctx
        if (handler is OverlordBot) {
            handler.onStart(ctx)
        }
        ensureWebSocketConnected()
        return ctx
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
            reviewAllBotsIsolated()
        }, 0, 1, TimeUnit.MINUTES)
    }

    override fun afterSingletonsInstantiated() {
        registerDiscoveredBots()
    }

    fun registerDiscoveredBots() {
        discoveredBots.forEach { bot ->
            val botMap = botMapService.getOrCreateBotMap(
                internalName = bot.descriptor.internalName,
                description = bot.descriptor.description,
            )
            if (!handlers.containsKey(botMap)) {
                startBot(botMap, bot)
            }
        }
    }

    /**
     * Reviews live contracts for each active bot in its own isolated transaction boundary.
     *
     * Fixes two concurrency/reliability issues from the previous design:
     * 1. Contract state sync (`ABORTED` detection) runs synchronously before `reviewContracts` rather than in an unawaited `async` block.
     * 2. Each bot executes in its own `transactionTemplate.execute` block so an error in one bot never rolls back another bot's work.
     */
    fun reviewAllBotsIsolated() {
        handlers.forEach { (botMap, handler) ->
            if (handler is OverlordBot && handler.descriptor.reviewInterval == null) {
                return@forEach
            }
            reviewSingleBot(botMap, handler)
        }
    }

    fun reviewSingleBot(botMap: BotMap, handler: MessageHandler) {
        transactionTemplate.execute { status ->
            try {
                logger.info("Reviewing ${botMap.internalName}")
                val rawContracts = contractService.getLiveContractsForBot(botMap.externalName)
                val ctx = contextsByBotMap[botMap] ?: contextFor(botMap.internalName, botMap.internalName)

                val activeContracts = mutableListOf<Contract>()
                for (c in rawContracts) {
                    val lockSession = c.lockSessionToken
                    if (!lockSession.isNullOrBlank()) {
                        try {
                            val jwt = botMapService.getBotJwt(botMap)
                            val contractInfo = coordinatorGateway.getContractBySession(
                                botName = botMap.externalName,
                                jwt = jwt,
                                lockSession = lockSession,
                                serial = c.serialNumber,
                            )
                            if (contractInfo.state == "ABORTED") {
                                logger.info("Aborted contract: ${c.externalContractName}")
                                c.state = ContractState.ABORTED
                                contractService.save(c)
                                if (handler is OverlordBot) {
                                    handler.onAbort(ctx, c)
                                }
                                continue
                            } else {
                                contractService.updateContractWithGetContractResponse(c, contractInfo)
                            }
                        } catch (syncEx: Exception) {
                            logger.warn("Could not sync contract ${c.name} for ${botMap.internalName}: ${syncEx.message}")
                        }
                    }
                    activeContracts.add(c)
                }

                if (handler is OverlordBot) {
                    handler.reviewContracts(ctx, activeContracts)
                } else {
                    handler.reviewContracts(activeContracts)
                }
            } catch (ex: Exception) {
                logger.error("Unhandled exception in reviewing contracts for ${botMap.internalName}: $ex", ex)
                status.setRollbackOnly()
            }
        }
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
            val authorJwt = botMapService.getAuthorJwt()
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
        if (!text.trimStart().startsWith("{")) {
            return
        }
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
                    EventType.StartedUpdate -> logger.debug("Received StartedUpdate for ${commonMetadata.lockSession}")
                    EventType.PeriodicUpdate -> logger.debug("Received PeriodicUpdate for ${commonMetadata.lockSession}")
                }
            } catch (ex: Exception) {
                logger.error("Encountered exception in processing WebSocket bot event", ex)
                status.setRollbackOnly()
            }
        }
    }

    @PreDestroy
    fun stop() {
        if (::botExecutorWatchdogService.isInitialized) {
            botExecutorWatchdogService.shutdown()
        }

        try {
            activeWebSocket?.sendClose(WebSocket.NORMAL_CLOSURE, "Shutting down")
        } catch (ex: Exception) {
            logger.warn("Error closing WebSocket: $ex")
        }
        activeWebSocket = null
        handlers.clear()
        contextsByInternalName.clear()
        contextsByBotMap.clear()

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
            coordinatorGateway.getContractBySession(botName, jwt, lockSession, serial.toInt())
        }
    }

    private fun resolveOrSyncContract(
        botMap: BotMap,
        signedEvent: SignedEvent,
        forceSync: Boolean = false,
    ): Contract {
        val e = signedEvent.payload!!
        val commonMetadata = e.metadata!!
        val serial = commonMetadata.contractSerialNumber.toInt()
        val lockSession = commonMetadata.lockSession!!

        if (!forceSync) {
            contractService.findContract(botMap.externalName, serial)?.let { existing ->
                if (existing.lockSessionToken.isNullOrBlank()) {
                    existing.lockSessionToken = lockSession
                    contractService.save(existing)
                }
                return existing
            }
        }

        val jwt = botMapService.getBotJwt(botMap)
        val contractInfo = coordinatorGateway.getContractBySession(
            botName = botMap.externalName,
            jwt = jwt,
            lockSession = lockSession,
            serial = serial,
        )
        logger.debug("Got contract info {}", contractInfo)

        val contract = contractService.getOrCreateContract(
            botMap.externalName,
            lockSession,
            serial,
        )
        return contractService.updateContractWithGetContractResponse(contract, contractInfo)
    }

    fun handleAccept(botMap: BotMap, signedEvent: SignedEvent) {
        logger.info("Handled accept")
        val contract = resolveOrSyncContract(botMap, signedEvent, forceSync = true)
        val handler = handlers[botMap]!!
        val ctx = contextsByBotMap[botMap] ?: contextFor(botMap.internalName, botMap.internalName)
        if (handler is OverlordBot) {
            handler.onAccept(ctx, contract)
        } else {
            handler.handleAccept(contract)
        }
    }

    fun handleRelease(botMap: BotMap, signedEvent: SignedEvent) {
        logger.info("Handle release")
        val e = signedEvent.payload!!
        val commonMetadata = e.metadata!!
        resolveOrSyncContract(botMap, signedEvent)

        val contract = contractService.markReleased(botMap.externalName, commonMetadata.contractSerialNumber.toInt())

        val handler = handlers[botMap]!!
        val ctx = contextsByBotMap[botMap] ?: contextFor(botMap.internalName, botMap.internalName)
        if (handler is OverlordBot) {
            handler.onRelease(ctx, contract)
        } else {
            handler.handleRelease(contract)
        }
    }

    fun handleLock(botMap: BotMap, signedEvent: SignedEvent) {
        logger.info("Handle Lock")
        val contract = resolveOrSyncContract(botMap, signedEvent)

        val handler = handlers[botMap]!!
        val ctx = contextsByBotMap[botMap] ?: contextFor(botMap.internalName, botMap.internalName)
        if (handler is OverlordBot) {
            handler.onLock(ctx, contract)
        } else {
            handler.handleLock(contract)
        }
    }

    fun handleUnlock(botMap: BotMap, signedEvent: SignedEvent) {
        logger.info("Handle Unlock")
        val contract = resolveOrSyncContract(botMap, signedEvent)

        val handler = handlers[botMap]!!
        val ctx = contextsByBotMap[botMap] ?: contextFor(botMap.internalName, botMap.internalName)
        if (handler is OverlordBot) {
            handler.onUnlock(ctx, contract)
        } else {
            handler.handleUnlock(contract)
        }
    }

    fun handleEmergencyUnlock(botMap: BotMap, signedEvent: SignedEvent) {
        logger.info("Handle Emergency Unlock")
        val contract = resolveOrSyncContract(botMap, signedEvent)

        val handler = handlers[botMap]
        val ctx = contextsByBotMap[botMap] ?: contextFor(botMap.internalName, botMap.internalName)
        if (handler is OverlordBot) {
            handler.onEmergencyUnlock(ctx, contract)
        } else {
            handler?.handleEmergencyUnlock(contract)
        }
    }

    @EventListener
    fun handleSendMessage(event: SendBotBytes) {
        val action = event.action
        try {
            val botJwt = botMapService.getBotJwt(event.botMap)
            val botName = event.botMap.externalName

            when (action) {
                is BotRestAction.CreateContract -> {
                    coordinatorGateway.createContract(
                        botName = botName,
                        botJwt = botJwt,
                        shareableToken = action.shareableToken,
                        signedMessageBase64 = action.signedMessageBase64,
                    )
                }
                is BotRestAction.CreateCommand -> {
                    coordinatorGateway.createCommand(
                        botName = botName,
                        botJwt = botJwt,
                        contractName = action.contractName,
                        shareableToken = action.shareableToken,
                        signedMessageBase64 = action.signedMessageBase64,
                    )
                }
                is BotRestAction.CreateMessage -> {
                    coordinatorGateway.addMessage(
                        botName = botName,
                        botJwt = botJwt,
                        contractName = action.contractName,
                        message = action.message,
                    )
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
            lastError = e
            if (it < maxAttempts - 1) Thread.sleep(delayMillis)
        }
    }

    throw lastError ?: NullPointerException("Unknown NPE in retryOnNullPointer")
}
