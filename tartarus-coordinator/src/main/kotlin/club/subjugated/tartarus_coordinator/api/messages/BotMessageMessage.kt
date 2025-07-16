package club.subjugated.tartarus_coordinator.api.messages

import club.subjugated.tartarus_coordinator.models.Message
import com.fasterxml.jackson.annotation.JsonFormat
import java.time.OffsetDateTime

data class BotMessageMessage(
    var name: String? = "",
    var botName: String? = "",
    var body: String? = "",
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
) {
    companion object {
        fun fromMessage(message : Message) : BotMessageMessage {
            val botName : String = if(message.bot == null) {
                "Deleted"
            } else {
                message.bot!!.name
            }

            return BotMessageMessage(
                name = message.name,
                body = message.body,
                botName = botName,
                createdAt = message.createdAt
            )
        }
    }
}
