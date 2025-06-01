package club.subjugated.overlord_exe.events

import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.Contract

class AddMessageToContract(
    val source: Any,
    val botMap: BotMap,
    val contract: Contract,
    val message: String
)