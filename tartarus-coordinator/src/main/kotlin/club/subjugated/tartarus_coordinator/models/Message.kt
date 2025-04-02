package club.subjugated.tartarus_coordinator.models

import club.subjugated.tartarus_coordinator.models.Contract.Companion.generateId
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.time.OffsetDateTime

@Entity
class Message(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId(),
    var body: String = "",
    @Enumerated(EnumType.STRING)
    var type: MessageType = MessageType.UNSPECIFIED,
    @ManyToOne @JoinColumn(name = "contract_id") var contract: Contract,
    @ManyToOne @JoinColumn(name = "bot_id") var bot: Bot,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) {
    companion object {
        fun generateId(): String {
            return club.subjugated.tartarus_coordinator.util.generateId("m-")
        }
    }
}