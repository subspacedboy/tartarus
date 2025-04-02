package club.subjugated.tartarus_coordinator.models

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.OffsetDateTime

@Entity
class Bot (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId(),
    @Enumerated(EnumType.STRING) var state: BotState = BotState.UNSPECIFIED,
    var publicKey: String,
    var description: String,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) {
    companion object {
        fun generateId(): String {
            return club.subjugated.tartarus_coordinator.util.generateId("b-")
        }
    }

    fun canReadMqtt(topic: String) : Boolean {
        return listOf("bots/inbox_api_${name}", "bots/inbox_events_${name}").contains(topic)
    }

    fun canWriteMqtt(topic: String) : Boolean {
        // Bots can always write to lock queues?
        if(topic.startsWith("locks/")) {
            return true
        }
        return listOf("status/${name}", "coordinator/inbox").contains(topic)
    }
}