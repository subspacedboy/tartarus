package club.subjugated.tartarus_coordinator.api.messages

import club.subjugated.tartarus_coordinator.models.KnownToken
import club.subjugated.tartarus_coordinator.models.LockSession
import club.subjugated.tartarus_coordinator.models.LockUserSession
import club.subjugated.tartarus_coordinator.util.getECPublicKeyFromCompressedKeyByteArray
import club.subjugated.tartarus_coordinator.util.getPemEncoding
import com.fasterxml.jackson.annotation.JsonFormat
import java.time.OffsetDateTime
import java.util.Base64

data class LockSessionMessage(
    var name: String = "",
    var publicKey: String = "",
    var publicPem: String = "",
    var shareToken: String = "",
    var totalControlToken: String? = "",
    var lockUserSession : LockUserSessionMessage? = null,
    var knownToken: KnownTokenMessage? = null,
    var lockState: LockStateMessage? = null,
    var availableForContract: Boolean? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) {
    companion object {
        fun fromLockSession(lockSession: LockSession,
                            lockUserSession: LockUserSession?,
                            knownToken: KnownToken?,
                            lockSessionArgs: List<LockSessionArgs>): LockSessionMessage {
            // Because javascript blows immeasurable ass, it's just easier to send
            // a PEM encoded version of the public key.
            val ecKey =
                getECPublicKeyFromCompressedKeyByteArray(
                    lockSession.decodePublicKey()
                )
            val pemKey = getPemEncoding(ecKey)
            return LockSessionMessage(
                name = lockSession.name,
                publicKey = lockSession.publicKey,
                publicPem = pemKey,
                shareToken = lockSession.shareToken!!,
                lockState = if(lockSessionArgs.contains(LockSessionArgs.SUPPRESS_LOCK_STATE)) null else LockStateMessage(lockSession.isLocked),
                totalControlToken = if(lockSessionArgs.contains(LockSessionArgs.SUPPRESS_TC_TOKEN)) "" else lockSession.totalControlToken,
                lockUserSession = if(lockUserSession == null) null else LockUserSessionMessage.fromLockUserSession(lockUserSession),
                availableForContract = if(lockSessionArgs.contains(LockSessionArgs.SUPPRESS_AVAILABLE_FOR_CONTRACT)) null else lockSession.availableForContract,
                knownToken = if(knownToken== null) null else KnownTokenMessage.fromKnownToken(knownToken),
                updatedAt = lockSession.updatedAt,
                createdAt = lockSession.createdAt,
            )
        }
    }
}
