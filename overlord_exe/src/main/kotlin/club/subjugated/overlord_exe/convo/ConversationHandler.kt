package club.subjugated.overlord_exe.convo

import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView

interface ConversationHandler {
    fun handle(convoId: String, message: ConvoDefsMessageView) : String
}