package club.subjugated.overlord_exe.components

import club.subjugated.overlord_exe.bots.timer_bot.events.IssueContract
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.util.TimeSource
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class BSkyDmMonitor(
    private val blueSkyService: BlueSkyService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val timeSource: TimeSource
) : Job {
    override fun execute(context: JobExecutionContext?) {
        println("Running job at ${System.currentTimeMillis()}")

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
                "timer" -> {}
                else -> {}
            }
        }
    }
}