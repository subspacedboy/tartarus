package club.subjugated.tartarus_coordinator.models

import club.subjugated.tartarus_coordinator.util.generateSalt
import club.subjugated.tartarus_coordinator.util.getECPublicKeyFromCompressedKeyByteArray
import club.subjugated.tartarus_coordinator.util.runSCryptWithCommonParams
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
    var mqttPassword: String? = null,
    var mqttSalt: String? = "",
    @JsonFormat(shape = JsonFormat.Shape.STRING) var lastValidated: OffsetDateTime? = null,
    @OneToMany(mappedBy = "id", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var commandQueue: MutableList<CommandQueue> = mutableListOf(),
    @OneToMany(mappedBy = "lockSession", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var lockUserSessions: MutableList<LockUserSession> = mutableListOf(),
    @OneToMany(mappedBy = "id", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var contracts: MutableList<Contract> = mutableListOf(),
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) : PublicKeyProvider {
    companion object {
        fun generateId(): String {
            return club.subjugated.tartarus_coordinator.util.generateId("ls-")
        }
    }

    override fun decodePublicKey(): ByteArray {
        return try {
            Base64.getDecoder().decode(this.publicKey)
        } catch (e : IllegalArgumentException) {
            Base64.getUrlDecoder().decode(this.publicKey)
        }
    }

    fun setPassword(input : String) {
        val salt = generateSalt()

        val derived = runSCryptWithCommonParams(input.toByteArray(), salt)

        this.mqttSalt = Base64.getEncoder().encodeToString(salt)
        this.mqttPassword = Base64.getEncoder().encodeToString(derived)
    }

    fun checkPassword(input : String) : Boolean {
        val salt = Base64.getDecoder().decode(this.mqttSalt)
        val derived = runSCryptWithCommonParams(input.toByteArray(), salt)
        return Base64.getEncoder().encodeToString(derived) == this.mqttPassword
    }

    fun shouldCheckPassword(): Boolean {
        return this.mqttPassword != null && this.mqttSalt != null
    }
}
