package club.subjugated.overlord_exe.integration.helpers

import club.subjugated.overlord_exe.services.BlueSkyService
import work.socialhub.kbsky.api.entity.app.bsky.graph.GraphGetListResponse
import work.socialhub.kbsky.model.app.bsky.actor.ActorDefsProfileView
import work.socialhub.kbsky.model.app.bsky.feed.FeedDefsPostView
import work.socialhub.kbsky.model.app.bsky.feed.FeedGetLikesLike
import work.socialhub.kbsky.model.app.bsky.graph.GraphDefsListView
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
        return "@test.user.fake"
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

    override fun addToList(subjectDid: String, listUri: String) {
        TODO("Not yet implemented")
    }

    override fun createList(name: String) {
        TODO("Not yet implemented")
    }

    override fun removeFromList(subjectDid: String, listUri: String) {
        TODO("Not yet implemented")
    }

    override fun getList(uri: String): GraphGetListResponse {
        TODO("Not yet implemented")
    }

    override fun getMyLists(): List<GraphDefsListView> {
        TODO("Not yet implemented")
    }

    override fun listNameToUri(name: String): String {
        TODO("Not yet implemented")
    }

    override fun getLastDmForConvo(
        convoId: String,
        subjectDid: String,
        onNewMessage: (String, ConvoDefsMessageView) -> Unit
    ) {
        TODO("Not yet implemented")
    }
}