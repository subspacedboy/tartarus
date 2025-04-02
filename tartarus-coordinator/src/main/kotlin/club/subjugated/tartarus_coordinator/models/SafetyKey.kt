package club.subjugated.tartarus_coordinator.models

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
class SafetyKey(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId(),
    @Enumerated(EnumType.STRING) var state: SafetyKeyState = SafetyKeyState.UNSPECIFIED,
    var privateKey: ByteArray? = null,
    var publicKey: ByteArray? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
) {
    companion object {
        fun generateId(): String {
            return club.subjugated.tartarus_coordinator.util.generateId("sa-")
        }
    }
}
