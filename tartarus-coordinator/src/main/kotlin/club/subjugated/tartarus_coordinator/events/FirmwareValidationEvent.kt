package club.subjugated.tartarus_coordinator.events

class FirmwareValidationEvent(
    val source: Any,
    val sessionToken: String,
    val validated: Boolean
)