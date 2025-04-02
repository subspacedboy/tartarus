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
    val lockState: LockStateMessage? = null,
    val messages : List<BotMessageMessage>? = mutableListOf(),
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) {
    companion object {
        fun fromContract(contract: Contract, skipMessages : Boolean = false): ContractMessage {
            return ContractMessage(
                name = contract.name,
                shareableToken = contract.shareableToken,
                state = contract.state.toString(),
                authorSessionName = contract.authorSession.name,
                body = Base64.getEncoder().encodeToString(contract.body),
                notes = contract.notes,
                messages = if(skipMessages) null else contract.messages.map { BotMessageMessage.fromMessage(it) },
                lockState = if(contract.lockState == null) null else LockStateMessage(contract.lockState!!),
                nextCounter = contract.nextCounter,
                serialNumber = contract.serialNumber,
                createdAt = contract.createdAt,
                updatedAt = contract.updatedAt,
            )
        }
    }
}
