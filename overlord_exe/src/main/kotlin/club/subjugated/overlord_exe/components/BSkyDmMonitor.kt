package club.subjugated.overlord_exe.components

import club.subjugated.overlord_exe.bots.simple_proxy.convo.SimpleProxyConvoHandler
import club.subjugated.overlord_exe.bots.superbot.convo.SuperBotConvoHandler
import club.subjugated.overlord_exe.bots.timer_bot.convo.TimerBotConvoHandler
import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.events.SendDmEvent
import club.subjugated.overlord_exe.intents.OverlordConversationHandler
import club.subjugated.overlord_exe.services.BSkyUserService
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.IntentService
import com.github.benmanes.caffeine.cache.Caffeine
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import work.socialhub.kbsky.ATProtocolException
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

@Component
class BSkyDmMonitor(
    private val blueSkyService: BlueSkyService,
    private val logger: Logger = LoggerFactory.getLogger(BSkyDmMonitor::class.java),
    private val timerBotConversationHandler: TimerBotConvoHandler,
    private val superBotConvoHandler: SuperBotConvoHandler,
    private val bSkyUserService: BSkyUserService,
    private val simpleProxyConvoHandler : SimpleProxyConvoHandler,
    private val overlordConversationHandler: OverlordConversationHandler,
    private val intentService: IntentService,
) : Job {
    override fun execute(context: JobExecutionContext?) {
        try {
            checkDms()
        } catch (ex : ATProtocolException) {
            logger.warn("ATProtocolException -> $ex")
        }
    }

    fun checkDms() {
        blueSkyService.getnewDms(::handleDm)
    }

    val lockMap = Caffeine.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build<String, ReentrantLock>()

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
            null // lock was already held, skip
        }
    }

    fun handleDm(convoId: String, message :  ConvoDefsMessageView, ignoreSend : Boolean = false) {
        val bskyUser = bSkyUserService.findOrCreateByDid(message.sender.did)
        val handle = blueSkyService.resolveDidToHandle(bskyUser.did)
        bskyUser.convoId = convoId
        bskyUser.handle = handle
        bSkyUserService.save(bskyUser)

        // If the message starts with "!" we treat it as a literal command
        var interpretAsCommand = message.text.startsWith("!")

        if(interpretAsCommand) {
            val messageMinusBang = message.text.replaceFirst("!","").trim()
            val chunks = messageMinusBang.split("\\s+".toRegex())
            val response : String = when(chunks[0].lowercase()) {
                "hello" -> {
                    "Hello 😈"
                }
                "contract" -> {
                    overlordConversationHandler.handle(convoId, message)
                }
                "accept" -> {
                    overlordConversationHandler.handle(convoId, message)
                }
                "token" -> {
                    simpleProxyConvoHandler.handle(convoId, message)
                }
                "own" -> {
                    simpleProxyConvoHandler.handle(convoId, message)
                }
                "submit" -> {
                    simpleProxyConvoHandler.handle(convoId, message)
                }
                "unlock" -> {
                    simpleProxyConvoHandler.handle(convoId, message)
                }
                "lock" -> {
                    simpleProxyConvoHandler.handle(convoId, message)
                }
                "release" -> {
                    simpleProxyConvoHandler.handle(convoId, message)
                }
                "timer" -> {
                    timerBotConversationHandler.handle(convoId, message)
                }
                "superbot" -> {
                    superBotConvoHandler.handle(convoId, message)
                }
                "add" -> {
                    try {
                        val list = chunks.slice(1 until chunks.size).joinToString(" ").lowercase()
                        val atUri = blueSkyService.listNameToUri(list)
                        blueSkyService.addToList(message.sender.did, atUri)
                        "Added"
                    } catch (e : Exception) {
                        val list = chunks.slice(1 until chunks.size).joinToString(" ").lowercase()
                        "Unknown list: $list"
                    }
                }
                "remove" -> {
                    try {
                        val list = chunks.slice(1 until chunks.size).joinToString(" ").lowercase()
                        val atUri = blueSkyService.listNameToUri(list)
                        blueSkyService.removeFromList(message.sender.did, atUri)
                        "Removed"
                    } catch (e : Exception) {
                        val list = chunks.slice(1 until chunks.size).joinToString(" ").lowercase()
                        "Unknown list: $list"
                    }

                }
//                "replay" -> {
//                    val oldConvoId = chunks[1]
//                    val subjectDid = chunks[2]
//                    blueSkyService.getLastDmForConvo(oldConvoId, subjectDid, { convoId, message ->
//                        handleDm(convoId, message, true)
//                    })
//                    "Done"
//                }
                else -> {
                    ""
                }
            }
            if(!ignoreSend) {
                blueSkyService.sendDm(convoId, response)
            }
        } else {
            withConversationLockIfAvailable(convoId) {
                val response = intentService.resolve(convoId, message.text)

                val responseText = if(response.intent != null) {
                    val ctx = ConversationContext(
                        bskyUser = bskyUser,
                        convoId = convoId,
                        currentMessage = message
                    )
                    val r = intentService.dispatch(ctx, response.intent)
                    r.text
                } else {
                    response.chat!!
                }

                if(!ignoreSend) {
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