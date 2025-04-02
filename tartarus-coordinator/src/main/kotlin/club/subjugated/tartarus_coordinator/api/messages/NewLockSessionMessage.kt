package club.subjugated.tartarus_coordinator.api.messages

data class NewLockSessionMessage(
    val publicKey: String,
    val sessionToken: String,
    val userSessionPublicKey: String)
