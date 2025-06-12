package club.subjugated.overlord_exe.convo

import club.subjugated.overlord_exe.services.Intent
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import kotlin.reflect.KClass

interface ConversationHandler {
    fun handle(convoId: String, message: ConvoDefsMessageView) : String
    fun getIntents() : List<KClass<out Intent>>
    fun handleIntent(ctx : ConversationContext, intent: Intent) : ConversationResponse
}