package club.subjugated.tartarus_coordinator.models

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.OffsetDateTime

@Entity
@Table(
    name = "event_delivery",
    indexes =
        [
            Index(name = "idx_delivery_bot_contract", columnList = "bot_id, contract_id"),
            Index(name = "idx_delivery_status", columnList = "bot_id, delivered, acknowledged"),
        ],
    uniqueConstraints =
        [UniqueConstraint(name = "uq_event_bot_delivery", columnNames = ["event_id", "bot_id"])],
)
class EventDelivery(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    var event: Event,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "bot_id", nullable = false) var bot: Bot,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    var contract: Contract,
    var delivered: Boolean = false,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var deliveredAt: OffsetDateTime? = null,
    var acknowledged: Boolean = false,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var acknowledgedAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
) {
    override fun toString(): String {
        return "EventDelivery(id=$id, event=${event.name}, bot=${bot.name}, " +
            "delivered=$delivered, acknowledged=$acknowledged)"
    }
}
