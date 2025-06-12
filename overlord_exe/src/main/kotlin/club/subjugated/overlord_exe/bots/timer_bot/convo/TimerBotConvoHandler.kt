package club.subjugated.overlord_exe.bots.timer_bot.convo

import club.subjugated.overlord_exe.bots.timer_bot.TimerBotRecordService
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
    val timerBotRecordService: TimerBotRecordService,
    val urlService: UrlService
) : ConversationHandler {
    override fun handle(
        convoId: String,
        message: ConvoDefsMessageView
    ): String {
        val authorDid = message.sender.did
        val record = timerBotRecordService.createInitialPlaceholderRecord(authorDid, convoId)
        return urlService.generateUrl("timer/${record.name}")
    }

    override fun getIntents(): List<KClass<out Intent>> {
        return listOf(Timer::class)
    }

    override fun handleIntent(
        ctx: ConversationContext,
        intent: Intent
    ): ConversationResponse {
        TODO("Not yet implemented")
    }
}