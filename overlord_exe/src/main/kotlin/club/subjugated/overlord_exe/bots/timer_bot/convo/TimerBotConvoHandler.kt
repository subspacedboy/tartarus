package club.subjugated.overlord_exe.bots.timer_bot.convo

import club.subjugated.overlord_exe.bots.timer_bot.TimerBot
import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.convo.ConversationHandler
import club.subjugated.overlord_exe.convo.ConversationResponse
import club.subjugated.overlord_exe.services.Intent
import club.subjugated.overlord_exe.services.UrlService
import org.springframework.stereotype.Component
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import kotlin.reflect.KClass

@Component
class TimerBotConvoHandler(
    val timerBot: TimerBot,
    val urlService: UrlService,
) : ConversationHandler {

    override val commands: Set<String> = setOf("timer")

    override fun handle(
        convoId: String,
        message: ConvoDefsMessageView,
    ): String {
        val authorDid = message.sender.did
        val session = timerBot.createInitialPlaceholderSession(authorDid, convoId)
        return urlService.generateUrl("timer/${session.name}")
    }

    override fun getIntents(): List<KClass<out Intent>> {
        return listOf(Timer::class)
    }

    override fun handleIntent(
        ctx: ConversationContext,
        intent: Intent,
    ): ConversationResponse {
        return when (intent) {
            is Timer -> {
                val authorDid = ctx.bskyUser!!.did
                val session = timerBot.createInitialPlaceholderSession(authorDid, ctx.convoId)
                val url = urlService.generateUrl("timer/${session.name}")
                ConversationResponse(text = url)
            }
            else -> throw IllegalStateException("Unknown type")
        }
    }
}
