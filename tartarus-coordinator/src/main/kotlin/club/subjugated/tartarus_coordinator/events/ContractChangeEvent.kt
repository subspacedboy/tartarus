package club.subjugated.tartarus_coordinator.events

import club.subjugated.tartarus_coordinator.models.Contract
import org.springframework.context.ApplicationEvent

class ContractChangeEvent(
    source: Any,
    val contract: Contract
) : ApplicationEvent(source) {
}