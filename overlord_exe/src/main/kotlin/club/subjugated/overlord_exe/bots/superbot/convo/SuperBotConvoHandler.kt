package club.subjugated.overlord_exe.bots.superbot.convo

import club.subjugated.overlord_exe.bots.superbot.SuperBot
import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.convo.ConversationHandler
import club.subjugated.overlord_exe.convo.ConversationResponse
import club.subjugated.overlord_exe.services.Intent
import club.subjugated.overlord_exe.services.UrlService
import org.springframework.stereotype.Component
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import kotlin.reflect.KClass

@Component
class SuperBotConvoHandler(
    private val superBot: SuperBot,
    private val urlService: UrlService,
) : ConversationHandler {

    override val commands: Set<String> = setOf("superbot")

    override fun handle(convoId: String, message: ConvoDefsMessageView): String {
        val authorDid = message.sender.did
        val name = superBot.createPlaceholder(authorDid, convoId)
        return urlService.generateUrl("superbot/$name")
    }

    override fun getIntents(): List<KClass<out Intent>> {
        return listOf(SuperbotIntent::class)
    }

    override fun handleIntent(
        ctx: ConversationContext,
        intent: Intent,
    ): ConversationResponse {
        return when (intent) {
            is SuperbotIntent -> {
                val authorDid = ctx.bskyUser!!.did
                val name = superBot.createPlaceholder(authorDid, ctx.convoId)
                ConversationResponse(
                    text = urlService.generateUrl("superbot/$name"),
                )
            }
            else -> throw IllegalStateException("Unknown intent")
        }
    }
}
