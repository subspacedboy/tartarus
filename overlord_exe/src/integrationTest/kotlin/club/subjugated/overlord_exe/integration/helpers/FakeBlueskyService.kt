package club.subjugated.overlord_exe.integration.helpers

import club.subjugated.overlord_exe.services.BlueSkyService
import work.socialhub.kbsky.model.app.bsky.actor.ActorDefsProfileView
import work.socialhub.kbsky.model.app.bsky.feed.FeedDefsPostView
import work.socialhub.kbsky.model.app.bsky.feed.FeedGetLikesLike
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import java.time.OffsetDateTime

class FakeBlueskyService : BlueSkyService {
    val sentMessages = mutableListOf<Pair<String, String>>()
    val fakeDms = mutableListOf<Pair<String, ConvoDefsMessageView>>()

    fun reset() {
        fakeDms.clear()
        sentMessages.clear()
    }

    override fun sendDm(convoId: String, message: String) {
        sentMessages.add(convoId to message)
    }

    override fun resolveDidToHandle(did: String): String {
        TODO("Not yet implemented")
    }

    override fun resolveHandleToDid(handle: String): String {
        TODO("Not yet implemented")
    }

    override fun post(message: String): String {
        TODO("Not yet implemented")
    }

    override fun getLikes(uri: String): List<FeedGetLikesLike> {
        TODO("Not yet implemented")
    }

    override fun getReposts(uri: String): List<ActorDefsProfileView> {
        TODO("Not yet implemented")
    }

    override fun traceThread(
        uri: String,
        action: (FeedDefsPostView) -> Unit
    ) {
        TODO("Not yet implemented")
    }

    override fun getAuthorFeedFromTime(
        authorDid: String,
        from: OffsetDateTime,
        onMessage: (FeedDefsPostView) -> Unit
    ) {

    }

    override fun getnewDms(onNewMessage: (String, ConvoDefsMessageView) -> Unit) {
        fakeDms.forEach { (convoId, messageView) ->
            onNewMessage(convoId, messageView)
        }
    }
}