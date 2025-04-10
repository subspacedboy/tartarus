package club.subjugated.tartarus_coordinator.models

import club.subjugated.tartarus_coordinator.models.LockSession.Companion.generateId
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
class CommandQueue(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId(),
    @ManyToOne @JoinColumn(name = "lock_session_id") var lockSession: LockSession,
    @OneToMany(mappedBy = "commandQueue", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var commands: MutableList<Command> = mutableListOf(),
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) {
    companion object {
        fun generateId(): String {
            return club.subjugated.tartarus_coordinator.util.generateId("q-")
        }
    }
}
