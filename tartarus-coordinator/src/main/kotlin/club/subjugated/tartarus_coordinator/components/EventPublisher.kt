package club.subjugated.tartarus_coordinator.components

import club.subjugated.tartarus_coordinator.events.AcknowledgedCommandEvent
import club.subjugated.tartarus_coordinator.events.ContractChangeEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class EventPublisher(private val webSocketHandler: EventWebSocketHandler) {

    @EventListener
    fun handleCustomEvent(event: AcknowledgedCommandEvent) {
        webSocketHandler.sendAuthorMessage(event.command.authorSession.name, event.toString())
        for(lu in event.command.commandQueue.lockSession.lockUserSessions) {
            webSocketHandler.sendLockUserMessage(lu.name, event.toString())
        }
    }

    @EventListener
    fun handleContractChangeEvent(event : ContractChangeEvent) {
        webSocketHandler.sendAuthorMessage(event.contract.authorSession.name, event.toString())
    }
}