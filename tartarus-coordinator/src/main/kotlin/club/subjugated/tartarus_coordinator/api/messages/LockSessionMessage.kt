package club.subjugated.tartarus_coordinator.api.messages

import club.subjugated.tartarus_coordinator.models.ContractState
import club.subjugated.tartarus_coordinator.models.KnownToken
import club.subjugated.tartarus_coordinator.models.LockSession
import club.subjugated.tartarus_coordinator.models.LockUserSession
import club.subjugated.tartarus_coordinator.util.getECPublicKeyFromCompressedKeyByteArray
import club.subjugated.tartarus_coordinator.util.getPemEncoding
import com.fasterxml.jackson.annotation.JsonFormat
import java.time.OffsetDateTime

data class LockSessionMessage(
    var name: String = "",
    var sessionToken: String? = "",
    var publicKey: String = "",
    var publicPem: String = "",
    var shareToken: String = "",
    var totalControlToken: String? = "",
    var lockUserSession: LockUserSessionMessage? = null,
    var knownToken: KnownTokenMessage? = null,
    var lockState: LockStateMessage? = null,
    var availableForContract: Boolean? = null,
    var activeContractName: String? = null,
    var activeContractSerial: Int? = null,
    var contractCount: Int = 0,
    var validatedFirmware: Boolean = false,
    var currentFirmware: String? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) {
    companion object {
        fun fromLockSession(
            lockSession: LockSession,
            lockUserSession: LockUserSession?,
            knownToken: KnownToken?,
            lockSessionArgs: List<LockSessionArgs>,
        ): LockSessionMessage {
            // Because javascript blows immeasurable ass, it's just easier to send
            // a PEM encoded version of the public key.
            val ecKey = getECPublicKeyFromCompressedKeyByteArray(lockSession.decodePublicKey())
            val pemKey = getPemEncoding(ecKey)
            val confirmedContract =
                lockSession.contracts.firstOrNull { it.state == ContractState.CONFIRMED }
            return LockSessionMessage(
                name = lockSession.name,
                sessionToken =
                    if (lockSessionArgs.contains(LockSessionArgs.INCLUDE_SESSION_TOKEN))
                        lockSession.sessionToken!!
                    else null,
                publicKey = lockSession.publicKey,
                publicPem = pemKey,
                shareToken = lockSession.shareToken!!,
                lockState =
                    if (lockSessionArgs.contains(LockSessionArgs.SUPPRESS_LOCK_STATE)) null
                    else
                        LockStateMessage(
                            isLocked = lockSession.isLocked,
                            emergencyUnlocked = lockSession.emergencyUnlocked,
                        ),
                totalControlToken =
                    if (lockSessionArgs.contains(LockSessionArgs.SUPPRESS_TC_TOKEN)) ""
                    else lockSession.totalControlToken,
                lockUserSession =
                    if (lockUserSession == null) null
                    else LockUserSessionMessage.fromLockUserSession(lockUserSession),
                availableForContract =
                    if (lockSessionArgs.contains(LockSessionArgs.SUPPRESS_AVAILABLE_FOR_CONTRACT))
                        null
                    else lockSession.availableForContract,
                knownToken =
                    if (knownToken == null) null else KnownTokenMessage.fromKnownToken(knownToken),
                activeContractName = confirmedContract?.name,
                activeContractSerial = confirmedContract?.serialNumber,
                contractCount = lockSession.contracts.size,
                validatedFirmware = lockSession.validatedFirmware,
                currentFirmware = lockSession.currentFirmware,
                updatedAt = lockSession.updatedAt,
                createdAt = lockSession.createdAt,
            )
        }
    }
}
