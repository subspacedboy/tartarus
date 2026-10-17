package club.subjugated.tartarus_coordinator.api.messages

import club.subjugated.tartarus_coordinator.models.Contract
import com.fasterxml.jackson.annotation.JsonFormat
import java.time.OffsetDateTime
import java.util.Base64

data class ContractMessage(
    val name: String,
    val publicKey: String? = null,
    val shareableToken: String? = null,
    val state: String? = null,
    val authorSessionName: String? = null,
    val body: String? = null,
    val notes: String? = null,
    val nextCounter: Int? = null,
    val serialNumber: Int? = null,
    val lockSessionName: String? = null,
    val lockSessionToken: String? = null,
    val lockState: LockStateMessage? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) val lockLastUpdate: OffsetDateTime? = null,
    val messages: List<BotMessageMessage>? = mutableListOf(),
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) {
    companion object {
        fun fromContract(contract: Contract, skipMessages: Boolean = false): ContractMessage {
            return ContractMessage(
                name = contract.name,
                shareableToken = contract.shareableToken,
                state = contract.state.toString(),
                authorSessionName = contract.authorSession.name,
                body = contract.body?.let { Base64.getEncoder().encodeToString(it) },
                notes = contract.notes,
                messages =
                    if (skipMessages) null
                    else contract.messages.map { BotMessageMessage.fromMessage(it) },
                lockSessionName = contract.lockSession.name,
                lockSessionToken = contract.lockSession.sessionToken,
                lockState =
                    if (contract.lockState == null) null
                    else
                        LockStateMessage(
                            isLocked = contract.lockState!!,
                            emergencyUnlocked = contract.emergencyUnlocked ?: false,
                        ),
                lockLastUpdate = contract.lockLastUpdate ?: contract.lockSession.updatedAt,
                nextCounter = contract.nextCounter,
                serialNumber = contract.serialNumber,
                createdAt = contract.createdAt,
                updatedAt = contract.updatedAt,
            )
        }
    }
}
