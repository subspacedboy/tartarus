package club.subjugated.overlord_exe.events

import club.subjugated.overlord_exe.models.StateMachine

class RequestInfo(
    val source: Any,
    val stateMachine: StateMachine,
    val formClass: String,
) {
}