package club.subjugated.overlord_exe.events

import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.Contract

class IssueLock(
    val source: Any,
    val botMap: BotMap,
    val contract: Contract
)