package club.subjugated.tartarus_coordinator.models

import club.subjugated.tartarus_coordinator.models.LockSession.Companion.generateId
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
class Contract(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    var name: String = generateId(),
    var publicKey : String,
    var shareableToken : String?,
    @Enumerated(EnumType.STRING)
    var state : ContractState = ContractState.UNSPECIFIED,
    @ManyToOne
    @JoinColumn(name = "author_id")
    var authorSession: AuthorSession,
    var body : ByteArray? = null,


    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) {
    companion object {
        fun generateId() : String {
            return club.subjugated.tartarus_coordinator.util.generateId("c-")
        }
    }
}