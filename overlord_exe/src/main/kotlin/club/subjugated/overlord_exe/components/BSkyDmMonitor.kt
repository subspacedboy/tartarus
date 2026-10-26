package club.subjugated.overlord_exe.components

import club.subjugated.overlord_exe.bots.simple_proxy.convo.SimpleProxyConvoHandler
import club.subjugated.overlord_exe.bots.superbot.convo.SuperBotConvoHandler
import club.subjugated.overlord_exe.bots.timer_bot.convo.TimerBotConvoHandler
import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.convo.ConversationHandler
import club.subjugated.overlord_exe.events.SendDmEvent
import club.subjugated.overlord_exe.intents.OverlordConversationHandler
import club.subjugated.overlord_exe.services.BSkyUserService
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.IntentService
import com.github.benmanes.caffeine.cache.Caffeine
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import work.socialhub.kbsky.ATProtocolException
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

@Component
@DisallowConcurrentExecution
class BSkyDmMonitor @Autowired constructor(
    private val blueSkyService: BlueSkyService,
    private val bSkyUserService: BSkyUserService,
    private val intentService: IntentService,
    private val conversationHandlers: List<ConversationHandler> = emptyList(),
    private val logger: Logger = LoggerFactory.getLogger(BSkyDmMonitor::class.java),
) : Job {

    private var fallbackHandlersByCommand: Map<String, (String, ConvoDefsMessageView, List<String>) -> String> = emptyMap()

    constructor(
        blueSkyService: BlueSkyService,
        logger: Logger = LoggerFactory.getLogger(BSkyDmMonitor::class.java),
        timerBotConversationHandler: TimerBotConvoHandler,
        superBotConvoHandler: SuperBotConvoHandler,
        bSkyUserService: BSkyUserService,
        simpleProxyConvoHandler: SimpleProxyConvoHandler,
        overlordConversationHandler: OverlordConversationHandler,
        intentService: IntentService,
    ) : this(
        blueSkyService = blueSkyService,
        bSkyUserService = bSkyUserService,
        intentService = intentService,
        conversationHandlers = listOf(
            timerBotConversationHandler,
            superBotConvoHandler,
            simpleProxyConvoHandler,
            overlordConversationHandler,
        ),
        logger = logger,
    ) {
        fallbackHandlersByCommand = mapOf(
            "contract" to { cId, msg, _ -> overlordConversationHandler.handle(cId, msg) },
            "accept" to { cId, msg, _ -> overlordConversationHandler.handle(cId, msg) },
            "token" to { cId, msg, _ -> simpleProxyConvoHandler.handle(cId, msg) },
            "own" to { cId, msg, _ -> simpleProxyConvoHandler.handle(cId, msg) },
            "submit" to { cId, msg, _ -> simpleProxyConvoHandler.handle(cId, msg) },
            "unlock" to { cId, msg, _ -> simpleProxyConvoHandler.handle(cId, msg) },
            "lock" to { cId, msg, _ -> simpleProxyConvoHandler.handle(cId, msg) },
            "release" to { cId, msg, _ -> simpleProxyConvoHandler.handle(cId, msg) },
            "timer" to { cId, msg, _ -> timerBotConversationHandler.handle(cId, msg) },
            "superbot" to { cId, msg, _ -> superBotConvoHandler.handle(cId, msg) },
        )
    }

    override fun execute(context: JobExecutionContext?) {
        try {
            checkDms()
        } catch (ex: ATProtocolException) {
            logger.warn("ATProtocolException -> $ex")
        }
    }

    fun checkDms() {
        blueSkyService.getnewDms(::handleDm)
    }

    val lockMap = Caffeine.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build<String, ReentrantLock>()

    val processedMessageIds = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.HOURS)
        .maximumSize(5000)
        .build<String, Boolean>()

    fun <T> withConversationLockIfAvailable(convoId: String, block: () -> T?): T? {
        val lock = lockMap.get(convoId) { ReentrantLock() }
        return if (lock.tryLock()) {
            try {
                block()
            } finally {
                lock.unlock()
            }
        } else {
            logger.debug("Unable to get lock, skipping convo")
            null
        }
    }

    private fun resolveCommandHandler(command: String): ConversationHandler? {
        return conversationHandlers.firstOrNull { handler ->
            val declared = try {
                handler.commands
            } catch (_: Exception) {
                emptySet()
            }
            command in declared
        }
    }

    fun handleDm(convoId: String, message: ConvoDefsMessageView, ignoreSend: Boolean = false) {
        val messageId: String? = message.id
        if (!messageId.isNullOrEmpty()) {
            if (processedMessageIds.getIfPresent(messageId) == true) {
                logger.debug("Message $messageId in convo $convoId already processed, skipping")
                return
            }
            processedMessageIds.put(messageId, true)
        }
        val bskyUser = bSkyUserService.findOrCreateByDid(message.sender.did)
        val handle = blueSkyService.resolveDidToHandle(bskyUser.did)
        bskyUser.convoId = convoId
        bskyUser.handle = handle
        bSkyUserService.save(bskyUser)

        val interpretAsCommand = message.text.startsWith("!")

        if (interpretAsCommand) {
            val messageMinusBang = message.text.replaceFirst("!", "").trim()
            val chunks = messageMinusBang.split("\\s+".toRegex())
            val cmd = chunks[0].lowercase()

            val registeredHandler = resolveCommandHandler(cmd)
            val response: String = when {
                registeredHandler != null -> registeredHandler.handle(convoId, message)
                cmd == "hello" -> "Hello 😈"
                cmd == "add" -> {
                    val list = chunks.slice(1 until chunks.size).joinToString(" ").lowercase()
                    try {
                        val atUri = blueSkyService.listNameToUri(list)
                        blueSkyService.addToList(message.sender.did, atUri)
                        "Added"
                    } catch (_: Exception) {
                        "Unknown list: $list"
                    }
                }
                cmd == "remove" -> {
                    val list = chunks.slice(1 until chunks.size).joinToString(" ").lowercase()
                    try {
                        val atUri = blueSkyService.listNameToUri(list)
                        blueSkyService.removeFromList(message.sender.did, atUri)
                        "Removed"
                    } catch (_: Exception) {
                        "Unknown list: $list"
                    }
                }
                cmd in fallbackHandlersByCommand -> fallbackHandlersByCommand.getValue(cmd).invoke(convoId, message, chunks)
                else -> ""
            }
            if (!ignoreSend && response.isNotBlank()) {
                blueSkyService.sendDm(convoId, response)
            }
        } else {
            withConversationLockIfAvailable(convoId) {
                val response = intentService.resolve(convoId, message.text)

                val responseText = if (response.intent != null) {
                    val ctx = ConversationContext(
                        bskyUser = bskyUser,
                        convoId = convoId,
                        currentMessage = message,
                    )
                    val r = intentService.dispatch(ctx, response.intent)
                    r.text
                } else {
                    response.chat!!
                }

                if (!ignoreSend && responseText.isNotBlank()) {
                    blueSkyService.sendDm(convoId, responseText)
                }
            }
        }
    }

    @EventListener
    fun handleDmMessage(event: SendDmEvent) {
        blueSkyService.sendDm(event.convoId, event.message)
    }
}
