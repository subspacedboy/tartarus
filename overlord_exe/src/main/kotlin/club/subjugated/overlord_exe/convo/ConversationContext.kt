package club.subjugated.overlord_exe.convo

import club.subjugated.overlord_exe.models.UserContext
import club.subjugated.overlord_exe.models.UserProfile
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView

data class ConversationContext(
    var userContext: UserContext?,
    var convoId: String,
    var currentMessage: ConvoDefsMessageView,
) {
    val userProfile: UserProfile?
        get() = userContext?.profile
}
