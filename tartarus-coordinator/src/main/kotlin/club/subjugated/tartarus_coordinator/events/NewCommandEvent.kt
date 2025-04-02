package club.subjugated.tartarus_coordinator.events

import org.springframework.context.ApplicationEvent

class NewCommandEvent(source: Any, val lockSessionToken: String) : ApplicationEvent(source)