package club.subjugated.overlord_exe.convo

import club.subjugated.overlord_exe.models.BSkyUser
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView

data class ConversationContext(
    var bskyUser : BSkyUser?,
    var convoId: String,
    var currentMessage: ConvoDefsMessageView
)