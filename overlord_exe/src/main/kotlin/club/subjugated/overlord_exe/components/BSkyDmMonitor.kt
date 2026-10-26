package club.subjugated.overlord_exe.components

import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.convo.ConversationHandler
import club.subjugated.overlord_exe.services.BSkyUserService
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.IntentService
import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import work.socialhub.kbsky.ATProtocolException
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

@Component
class BSkyDmMonitor @Autowired constructor(
    private val blueSkyService: BlueSkyService,
    private val bSkyUserService: BSkyUserService,
    private val intentService: IntentService,
    private val conversationHandlers: List<ConversationHandler> = emptyList(),
    @Autowired(required = false) private val environment: Environment? = null,
    private val logger: Logger = LoggerFactory.getLogger(BSkyDmMonitor::class.java),
) {
    private val pollLock = ReentrantLock()

    @Scheduled(fixedDelay = 4000)
    fun scheduledCheckDms() {
        if (environment?.activeProfiles?.contains("test") == true) {
            return
        }
        if (!pollLock.tryLock()) {
            return
        }
        try {
            checkDms()
        } catch (ex: ATProtocolException) {
            logger.warn("ATProtocolException -> $ex")
        } finally {
            pollLock.unlock()
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

        if (message.text.startsWith("!")) {
            val messageMinusBang = message.text.replaceFirst("!", "").trim()
            val chunks = messageMinusBang.split("\\s+".toRegex())
            val cmd = chunks[0].lowercase()

            val registeredHandler = resolveCommandHandler(cmd)
            val response: String = when {
                registeredHandler != null -> registeredHandler.handle(convoId, message)
                cmd == "hello" -> "Hello 😈"
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
}
