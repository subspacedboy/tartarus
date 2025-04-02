package club.subjugated.tartarus_coordinator.api.messages

data class UpdateKnownTokenMessage(
    val name: String,
    val notes: String
)
