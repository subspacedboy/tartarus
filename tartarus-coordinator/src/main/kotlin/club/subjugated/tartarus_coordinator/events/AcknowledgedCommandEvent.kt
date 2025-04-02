package club.subjugated.tartarus_coordinator.events

import club.subjugated.fb.message.Acknowledgement
import club.subjugated.tartarus_coordinator.models.Command
import org.springframework.context.ApplicationEvent

class AcknowledgedCommandEvent(
    source: Any,
    val command: Command,
    val acknowledgement: Acknowledgement,
) : ApplicationEvent(source) {}
