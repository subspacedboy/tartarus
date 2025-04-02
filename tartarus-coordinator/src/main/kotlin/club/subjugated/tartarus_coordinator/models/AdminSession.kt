package club.subjugated.tartarus_coordinator.models

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.OffsetDateTime
import java.util.*

@Entity
class AdminSession(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId(),
    var publicKey: String,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) : PublicKeyProvider {
    companion object {
        fun generateId(): String {
            return club.subjugated.tartarus_coordinator.util.generateId("ad-")
        }
    }

    override fun decodePublicKey(): ByteArray {
        return Base64.getDecoder().decode(this.publicKey)
    }
}