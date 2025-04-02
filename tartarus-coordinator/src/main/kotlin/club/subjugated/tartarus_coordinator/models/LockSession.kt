package club.subjugated.tartarus_coordinator.models

import club.subjugated.tartarus_coordinator.util.getECPublicKeyFromCompressedKeyByteArray
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.*
import java.security.interfaces.ECPublicKey
import java.time.OffsetDateTime
import java.util.*

@Entity
class LockSession(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId(),
    var publicKey: String,
    var sessionToken: String?,
    var shareToken: String?,
    var totalControlToken: String?,
    var isLocked: Boolean = false,
    var availableForContract: Boolean = true,
    var validatedFirmware: Boolean = false,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var lastValidated: OffsetDateTime? = null,
    @OneToMany(mappedBy = "id", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var commandQueue: MutableList<CommandQueue> = mutableListOf(),
    @OneToMany(mappedBy = "lockSession", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var lockUserSessions: MutableList<LockUserSession> = mutableListOf(),
    @OneToMany(mappedBy = "id", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var contracts: MutableList<Contract> = mutableListOf(),
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) {
    companion object {
        fun generateId(): String {
            return club.subjugated.tartarus_coordinator.util.generateId("ls-")
        }
    }

    fun decodePublicKey(): ByteArray {
        return Base64.getDecoder().decode(this.publicKey)
    }

//    fun loadPublicKey(): ECPublicKey {
//        return getECPublicKeyFromCompressedKeyByteArray(Base64.getDecoder().decode(this.publicKey))
//    }
}
