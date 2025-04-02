package club.subjugated.tartarus_coordinator.api.messages

import club.subjugated.tartarus_coordinator.models.LockSession
import club.subjugated.tartarus_coordinator.util.getECPublicKeyFromCompressedKeyByteArray
import club.subjugated.tartarus_coordinator.util.getPemEncoding
import com.fasterxml.jackson.annotation.JsonFormat
import java.time.OffsetDateTime
import java.util.Base64

data class LockSessionMessage(
    var name : String = "",
    var publicKey: String = "",
    var publicPem: String = "",
    var shareToken: String = "",
    var totalControlToken: String? = "",
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) {
    companion object {
        fun fromLockSession(lockSession : LockSession) : LockSessionMessage {
            // Because javascript blows immeasurable ass, it's just easier to send
            // a PEM encoded version of the public key.
            val ecKey = getECPublicKeyFromCompressedKeyByteArray(Base64.getUrlDecoder().decode(lockSession.publicKey))
            val pemKey = getPemEncoding(ecKey)
            return LockSessionMessage(
                name = lockSession.name,
                publicKey = lockSession.publicKey,
                publicPem = pemKey,
                shareToken = lockSession.shareToken!!,
                totalControlToken = lockSession.totalControlToken,
                updatedAt = lockSession.updatedAt,
                createdAt = lockSession.createdAt
            )
        }
    }
}