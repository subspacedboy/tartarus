package club.subjugated.tartarus_coordinator.events

import club.subjugated.tartarus_coordinator.models.Command
import org.springframework.context.ApplicationEvent

class ErroredCommandEvent(
    source: Any,
    val command: Command,
    val message: String?,
) : ApplicationEvent(source) {
    override fun toString(): String {
        return "ErroredCommandEvent(command=$command, message=$message)"
    }
}
