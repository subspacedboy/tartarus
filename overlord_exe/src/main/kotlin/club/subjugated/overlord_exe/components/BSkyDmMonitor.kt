package club.subjugated.overlord_exe.components

import club.subjugated.overlord_exe.bots.announcer.events.ConnectIdentityEvent
import club.subjugated.overlord_exe.bots.timer_bot.events.IssueContract
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.util.TimeSource
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import work.socialhub.kbsky.ATProtocolException

@Component
class BSkyDmMonitor(
    private val blueSkyService: BlueSkyService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val timeSource: TimeSource,
    private val logger: Logger = LoggerFactory.getLogger(BSkyDmMonitor::class.java)
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
                    val shareableToken = chunks[1]
                    val amount = chunks[2].toLong()
                    val unit = chunks[3]
                    val isPublic = chunks[4].toBoolean()
                    val authorDid = message.sender.did

                    applicationEventPublisher.publishEvent(
                        club.subjugated.overlord_exe.bots.timer_bot.events.IssueContract(
                            source = this,
                            shareableToken = shareableToken,
                            amount = amount,
                            unit = unit,
                            public = isPublic,
                            did = authorDid
                        )
                    )

                    blueSkyService.sendDm(convoId, "Contract issued")
                }
                else -> {}
            }
        }
    }
}