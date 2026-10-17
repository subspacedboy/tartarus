package club.subjugated.tartarus_coordinator.models

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.OffsetDateTime

@Entity
@Table(
    name = "event",
    indexes =
        [
            Index(name = "idx_event_contract_counter", columnList = "contract_id, counter"),
            Index(name = "idx_event_session", columnList = "sessionToken"),
        ],
    uniqueConstraints =
        [
            UniqueConstraint(
                name = "uq_contract_event_counter",
                columnNames = ["contract_id", "counter"],
            )
        ],
)
class Event(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId(),
    var sessionToken: String = "",
    var contractSerialNumber: Int = 0,
    var counter: Int = 0,
    var eventType: String = "",
    var correlationId: String? = null,
    @Lob @Column(columnDefinition = "BLOB") var body: ByteArray? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lock_session_id")
    var lockSession: LockSession? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    var contract: Contract? = null,
    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var deliveries: MutableList<EventDelivery> = mutableListOf(),
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
) {
    companion object {
        fun generateId(): String {
            return club.subjugated.tartarus_coordinator.util.generateId("ev-")
        }
    }

    override fun toString(): String {
        return "Event(name='$name', sessionToken='$sessionToken', contractSerial=$contractSerialNumber, " +
            "counter=$counter, eventType='$eventType', correlationId=$correlationId)"
    }
}
