package club.subjugated.overlord_exe.components

import club.subjugated.overlord_exe.bots.announcer.events.ConnectIdentityEvent
import club.subjugated.overlord_exe.bots.bsky_selflock.convo.BSkySelfLockConvoHandler
import club.subjugated.overlord_exe.bots.superbot.convo.SuperBotConvoHandler
import club.subjugated.overlord_exe.bots.timer_bot.convo.TimerBotConvoHandler
import club.subjugated.overlord_exe.events.IssueContract
import club.subjugated.overlord_exe.convo.ConversationHandler
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.encodePublicKeySecp1
import club.subjugated.overlord_exe.util.loadECPublicKeyFromPkcs8
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import work.socialhub.kbsky.ATProtocolException
import java.security.interfaces.ECPublicKey

class SendDmEvent (
    val source: Any,
    val message: String,
    val did: String,
    val convoId: String
)

@Component
class BSkyDmMonitor(
    private val blueSkyService: BlueSkyService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val timeSource: TimeSource,
    private val logger: Logger = LoggerFactory.getLogger(BSkyDmMonitor::class.java),
    private val bSkySelfLockConvoHandler: BSkySelfLockConvoHandler,
    private val timerBotConversationHandler: TimerBotConvoHandler,
    private val superBotConvoHandler: SuperBotConvoHandler
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
            when(chunks[0]) {
                "bsky_selflock" -> {
                    val response = bSkySelfLockConvoHandler.handle(convoId, message)
                    blueSkyService.sendDm(convoId, response)
                }
                "bsky_likes" -> {
                    val shareableToken = chunks[1]
                    val goal = chunks[2].toLong()

                    val authorDid = message.sender.did

                    applicationEventPublisher.publishEvent(
                        club.subjugated.overlord_exe.bots.bsky_likes.events.IssueContract(
                            source = this,
                            shareableToken = shareableToken,
                            goal = goal,
                            did = authorDid
                        )
                    )
                    blueSkyService.sendDm(convoId, "Issued")
                }
                "announcer" -> {
                    val token = chunks[1]
                    val sourceDid = message.sender.did
                    applicationEventPublisher.publishEvent(ConnectIdentityEvent(this, token, sourceDid))

                    blueSkyService.sendDm(convoId, "Updated")
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