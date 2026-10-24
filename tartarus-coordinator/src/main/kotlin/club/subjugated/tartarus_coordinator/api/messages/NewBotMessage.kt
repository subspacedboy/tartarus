package club.subjugated.tartarus_coordinator.api.messages

data class NewBotMessage(
    val publicKey: String,
    val description: String,
    val internalName: String? = null,
    val keyVersion: Int = 1,
)

data class UpdateBotMessage(
    val description: String? = null,
    val state: String? = null,
    val publicKey: String? = null,
    val keyVersion: Int? = null,
)

data class SyncBotsRequest(val bots: List<NewBotMessage>)
