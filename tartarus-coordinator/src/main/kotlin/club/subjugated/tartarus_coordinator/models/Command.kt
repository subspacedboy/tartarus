package club.subjugated.tartarus_coordinator.models

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
class Command(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    var name: String = generateId(),

    @Enumerated(EnumType.STRING)
    var state : CommandState = CommandState.UNSPECIFIED,
    @Enumerated(EnumType.STRING)
    var type: CommandType = CommandType.UNSPECIFIED,
    var body : ByteArray? = null,
    var message: String? = null,

    var serialNumber: Int? = null,
    var counter: Int? = null,

    @ManyToOne
    @JoinColumn(name = "author_id")
    var authorSession: AuthorSession,

    @ManyToOne
    @JoinColumn(name = "command_queue_id")
    var commandQueue: CommandQueue,

    @ManyToOne
    @JoinColumn(name = "contract_id")
    var contract: Contract,

    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) {
    companion object {
        fun generateId() : String {
            return club.subjugated.tartarus_coordinator.util.generateId("co-")
        }
    }
}