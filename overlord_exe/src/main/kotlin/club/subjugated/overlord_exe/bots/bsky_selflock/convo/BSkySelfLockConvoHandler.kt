package club.subjugated.overlord_exe.bots.bsky_selflock.convo

import club.subjugated.overlord_exe.bots.bsky_selflock.BSkySelfLockService
import club.subjugated.overlord_exe.convo.ConversationHandler
import club.subjugated.overlord_exe.services.UrlService
import club.subjugated.overlord_exe.util.generateId
import org.springframework.stereotype.Component
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView

@Component
class BSkySelfLockConvoHandler(
    val selfLockService: BSkySelfLockService,
    val urlService: UrlService
) : ConversationHandler {
    override fun handle(convoId: String, message: ConvoDefsMessageView) : String {
        val authorDid = message.sender.did
        val name = selfLockService.createPlaceholder(authorDid, convoId)
        return urlService.generateUrl("bsky_selflock/$name")
    }
}