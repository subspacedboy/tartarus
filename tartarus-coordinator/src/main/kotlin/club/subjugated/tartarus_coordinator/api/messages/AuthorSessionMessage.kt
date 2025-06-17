package club.subjugated.tartarus_coordinator.api.messages

import club.subjugated.tartarus_coordinator.models.AuthorSession

data class AuthorSessionMessage(
    var sessionToken: String? = null,
    var name: String? = null
) {
    companion object {
        fun fromAuthorSession(authorSession: AuthorSession): AuthorSessionMessage {
            return AuthorSessionMessage(
                sessionToken = authorSession.name,
                name = authorSession.name)
        }
    }
}
