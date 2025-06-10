package club.subjugated.overlord_exe.components

import club.subjugated.overlord_exe.bots.simple_proxy.SimpleProxyService
import club.subjugated.overlord_exe.bots.simple_proxy.convo.SimpleProxyConvoHandler
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
    private val simpleProxyService: SimpleProxyService,
    private val simpleProxyConvoHandler : SimpleProxyConvoHandler
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

            val response : String = when(chunks[0].lowercase()) {
                "hello" -> {
                    "Hello 😈"
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

                else -> { "??? ${chunks[0]}" }
            }

            blueSkyService.sendDm(convoId, response)
        }
    }

    @EventListener
    fun handleDmMessage(event: SendDmEvent) {
        blueSkyService.sendDm(event.convoId, event.message)
    }
}