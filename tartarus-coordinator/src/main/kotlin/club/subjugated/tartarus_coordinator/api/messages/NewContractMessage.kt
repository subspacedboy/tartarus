package club.subjugated.tartarus_coordinator.api.messages

data class NewContractMessage(
    val contractName: String? = null,
    val signedMessage: String? = null,
)
