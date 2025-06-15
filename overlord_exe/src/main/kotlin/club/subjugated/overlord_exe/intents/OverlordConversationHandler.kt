package club.subjugated.overlord_exe.intents

import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.convo.ConversationHandler
import club.subjugated.overlord_exe.convo.ConversationResponse
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.Intent
import org.springframework.stereotype.Component
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import kotlin.reflect.KClass

@Component
class OverlordConversationHandler(
    private val blueSkyService: BlueSkyService
) : ConversationHandler {
    override fun handle(
        convoId: String,
        message: ConvoDefsMessageView
    ): String {
        TODO("Not yet implemented")
    }

    override fun getIntents(): List<KClass<out Intent>> {
        return listOf(AddToListIntent::class,
            RemoveFromListIntent::class,
            SayHelloIntent::class)
    }

    override fun handleIntent(
        ctx: ConversationContext,
        intent: Intent
    ): ConversationResponse {
        val response = when(intent) {
            is SayHelloIntent -> {
                ConversationResponse(
                    text = "Hello 😈! I'm Overlord-exe."
                )
            }
            is AddToListIntent -> {
                val atUri = blueSkyService.listNameToUri(intent.addToListData.listName!!)
                blueSkyService.addToList(ctx.bskyUser!!.did, atUri)
                ConversationResponse(
                    text = "Added to ${intent.addToListData.listName}"
                )
            }
            is RemoveFromListIntent -> {
                val atUri = blueSkyService.listNameToUri(intent.removeFromListData.listName!!)
                blueSkyService.removeFromList(ctx.bskyUser!!.did, atUri)
                ConversationResponse(
                    text = "Removed from ${intent.removeFromListData.listName}"
                )
            }

            else -> {
                throw IllegalStateException("Unknown intent")
            }
        }

        return response
    }
}