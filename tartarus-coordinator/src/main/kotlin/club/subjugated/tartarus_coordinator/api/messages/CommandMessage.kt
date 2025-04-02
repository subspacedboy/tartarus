package club.subjugated.tartarus_coordinator.api.messages

import club.subjugated.tartarus_coordinator.models.Command
import com.fasterxml.jackson.annotation.JsonFormat
import java.time.OffsetDateTime
import java.util.Base64

data class CommandMessage(
    val name: String,
    val authorName : String,
    val contractName : String,
    val state : String,
    val type : String,
    val message : String?,
    val body : String,
    val counter: Int? = null,
    val serialNumber: Int? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) {
    companion object {
        fun fromCommand(command: Command): CommandMessage {
            return CommandMessage(
                name = command.name,
                authorName = command.authorSession.name,
                contractName = command.contract.name,
                state = command.state.toString(),
                type = command.type.toString(),
                message = command.message,
                body = Base64.getEncoder().encodeToString(command.body),
                counter = command.counter,
                serialNumber = command.serialNumber,
                createdAt = command.createdAt,
                updatedAt = command.updatedAt
            )
        }
    }
}