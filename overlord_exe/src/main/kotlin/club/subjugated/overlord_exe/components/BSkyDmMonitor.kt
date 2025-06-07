package club.subjugated.overlord_exe.components

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
    private val bSkyUserService: BSkyUserService
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
            bskyUser.convoId = convoId
            bSkyUserService.save(bskyUser)

            when(chunks[0]) {
//                "bsky_likes" -> {
//                    val shareableToken = chunks[1]
//                    val goal = chunks[2].toLong()
//
//                    val authorDid = message.sender.did
//
//                    applicationEventPublisher.publishEvent(
//                        club.subjugated.overlord_exe.bots.bsky_likes.events.IssueContract(
//                            source = this,
//                            shareableToken = shareableToken,
//                            goal = goal,
//                            did = authorDid
//                        )
//                    )
//                    blueSkyService.sendDm(convoId, "Issued")
//                }
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