package club.subjugated.tartarus_coordinator.api.messages

import club.subjugated.tartarus_coordinator.models.AuthorSession

data class AuthorSessionMessage(
    var sessionToken : String? = null
) {
    companion object {
        fun fromAuthorSession(authorSession : AuthorSession) : AuthorSessionMessage {
            return AuthorSessionMessage(
                sessionToken = authorSession.name
            )
        }
    }
}
