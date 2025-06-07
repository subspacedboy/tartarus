package club.subjugated.overlord_exe.components

import club.subjugated.overlord_exe.bots.simple_proxy.SimpleProxyService
import club.subjugated.overlord_exe.bots.superbot.convo.SuperBotConvoHandler
import club.subjugated.overlord_exe.bots.timer_bot.convo.TimerBotConvoHandler
import club.subjugated.overlord_exe.events.SendDmEvent
import club.subjugated.overlord_exe.services.BSkyUserService
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.util.TimeSource
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import work.socialhub.kbsky.ATProtocolException

@Component
class BSkyDmMonitor(
    private val blueSkyService: BlueSkyService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val timeSource: TimeSource,
    private val logger: Logger = LoggerFactory.getLogger(BSkyDmMonitor::class.java),
    private val timerBotConversationHandler: TimerBotConvoHandler,
    private val superBotConvoHandler: SuperBotConvoHandler,
    private val bSkyUserService: BSkyUserService,
    private val simpleProxyService: SimpleProxyService
) : Job {
    override fun execute(context: JobExecutionContext?) {
        try {
            checkDms()
        } catch (ex : ATProtocolException) {
            logger.warn("ATProtocolException -> $ex")
        }
    }

    fun checkDms() {
        blueSkyService.getnewDms() { convoId, message ->
            val chunks = message.text.trim().split("\\s+".toRegex())

            val bskyUser = bSkyUserService.findOrCreateByDid(message.sender.did)
            val handle = blueSkyService.resolveDidToHandle(bskyUser.did)
            bskyUser.convoId = convoId
            bskyUser.handle = handle
            bSkyUserService.save(bskyUser)

            when(chunks[0]) {
                "hello" -> {
                    blueSkyService.sendDm(convoId, "😈")
                }
                "token" -> {
                    bskyUser.shareableToken = chunks[1]
                    bSkyUserService.save(bskyUser)
                }
                "own" -> {
                    val otherUser = bSkyUserService.findByHandle(chunks[1])
                    val response = if(otherUser == null) {
                        "User hasn't signed up..."
                    } else {
                        // Current user is key holder, second user is sub
                        simpleProxyService.issueContract(bskyUser, otherUser)
                        "Issuing contract"
                    }

                    blueSkyService.sendDm(convoId, response)
                }
                "submit" -> {
                    val otherUser = bSkyUserService.findByHandle(chunks[1])
                    val response = if(otherUser == null) {
                        "User hasn't signed up..."
                    } else {
                        simpleProxyService.issueContract(otherUser, bskyUser)
                        "Issuing contract"
                    }

                    blueSkyService.sendDm(convoId, response)
                }
                "release" -> {
                    val otherUser = bSkyUserService.findByHandle(chunks[1])
                    val response = if(otherUser == null) {
                        "User hasn't signed up..."
                    } else {
                        simpleProxyService.release(otherUser, bskyUser)
                        "Issuing release"
                    }
                    blueSkyService.sendDm(convoId, response)
                }
                "unlock" -> {
                    val otherUser = bSkyUserService.findByHandle(chunks[1])
                    val response = if(otherUser == null) {
                        "User hasn't signed up..."
                    } else {
                        simpleProxyService.unlock(bskyUser, otherUser)
                        "Unlocking"
                    }
                    blueSkyService.sendDm(convoId, response)
                }
                "lock" -> {
                    val otherUser = bSkyUserService.findByHandle(chunks[1])
                    val response = if(otherUser == null) {
                        "User hasn't signed up..."
                    } else {
                        simpleProxyService.lock(bskyUser, otherUser)
                        "Locking"
                    }
                    blueSkyService.sendDm(convoId, response)
                }

                "timer" -> {
                    val response = timerBotConversationHandler.handle(convoId, message)
                    blueSkyService.sendDm(convoId, response)
                }
                "own_me" -> {
                    val response = superBotConvoHandler.handle(convoId, message)
                    blueSkyService.sendDm(convoId, response)
                }
                else -> {}
            }
        }
    }

    @EventListener
    fun handleDmMessage(event: SendDmEvent) {
        blueSkyService.sendDm(event.convoId, event.message)
    }
}