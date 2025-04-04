package club.subjugated.tartarus_coordinator.models

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.OffsetDateTime

@Entity
class FirmwareUpgradePath(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId(),
    var fromVersion: String = "",
    var toVersion: String = "",
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
) {
    companion object {
        fun generateId(): String {
            return club.subjugated.tartarus_coordinator.util.generateId("fu-")
        }
    }
}