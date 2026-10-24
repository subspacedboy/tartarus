package club.subjugated.overlord_exe.events

import club.subjugated.overlord_exe.models.BotMap

sealed class BotRestAction {
    data class CreateContract(
        val shareableToken: String,
        val signedMessageBase64: String,
    ) : BotRestAction()

    data class CreateCommand(
        val contractName: String,
        val shareableToken: String? = null,
        val signedMessageBase64: String,
    ) : BotRestAction()

    data class CreateMessage(
        val contractName: String,
        val message: String,
    ) : BotRestAction()
}

class SendBotBytes(
    val source: Any,
    val botMap: BotMap,
    val action: BotRestAction,
)
