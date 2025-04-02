package club.subjugated.tartarus_coordinator.api.messages

data class NewContractMessage(
    val shareableToken: String? = null,
    val authorName: String? = null,
    val signedMessage: String? = null,
    val notes: String? = null,
)
