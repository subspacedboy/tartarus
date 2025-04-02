package club.subjugated.tartarus_coordinator.api.messages

data class NewCommandMessage(
    var shareableToken: String? = null,
    var contractName: String? = null,
    var authorSessionName: String? = null,
    val signedMessage: String? = null,
) {}
