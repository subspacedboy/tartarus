package club.subjugated.tartarus_coordinator.api.messages

import club.subjugated.tartarus_coordinator.models.Event
import club.subjugated.tartarus_coordinator.models.EventDelivery
import com.fasterxml.jackson.annotation.JsonFormat
import java.time.OffsetDateTime

data class EventDeliveryMessage(
    val botName: String,
    val delivered: Boolean,
    @JsonFormat(shape = JsonFormat.Shape.STRING) val deliveredAt: OffsetDateTime? = null,
) {
    companion object {
        fun fromDelivery(delivery: EventDelivery): EventDeliveryMessage {
            return EventDeliveryMessage(
                botName = delivery.bot.name,
                delivered = delivery.delivered,
                deliveredAt = delivery.deliveredAt,
            )
        }
    }
}

data class EventMessage(
    val name: String,
    val sessionToken: String,
    val contractName: String? = null,
    val contractSerialNumber: Int,
    val counter: Int,
    val eventType: String,
    val correlationId: String? = null,
    val deliveries: List<EventDeliveryMessage> = emptyList(),
    @JsonFormat(shape = JsonFormat.Shape.STRING) val createdAt: OffsetDateTime? = null,
) {
    companion object {
        fun fromEvent(event: Event): EventMessage {
            return EventMessage(
                name = event.name,
                sessionToken = event.sessionToken,
                contractName = event.contract?.name,
                contractSerialNumber = event.contractSerialNumber,
                counter = event.counter,
                eventType = event.eventType,
                correlationId = event.correlationId,
                deliveries = event.deliveries.map { EventDeliveryMessage.fromDelivery(it) },
                createdAt = event.createdAt,
            )
        }
    }
}
