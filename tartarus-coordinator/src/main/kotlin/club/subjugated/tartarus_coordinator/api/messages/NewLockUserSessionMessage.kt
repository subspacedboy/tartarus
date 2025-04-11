package club.subjugated.tartarus_coordinator.api.messages

data class NewLockUserSessionMessage(
    var sessionToken: String?,
    var nonce: String?,
    var cipher: String?,
    var lockUserSessionPublicKey: String?
)
