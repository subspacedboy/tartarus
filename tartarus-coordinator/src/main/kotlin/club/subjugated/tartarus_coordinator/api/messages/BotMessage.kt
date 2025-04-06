package club.subjugated.tartarus_coordinator.api.messages

import club.subjugated.tartarus_coordinator.models.Bot
import com.fasterxml.jackson.annotation.JsonFormat
import java.time.OffsetDateTime

class BotMessage(
    var name: String?,
    var publicKey: String?,
    var description: String?,
    var clearTextPassword: String?,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) {
    companion object {
        fun fromBot(bot : Bot) : BotMessage {
            return BotMessage(
                name = bot.name,
                publicKey = bot.publicKey,
                clearTextPassword = bot.clearTextPassword,
                description = bot.description,
                createdAt = bot.createdAt,
                updatedAt = bot.updatedAt
            )
        }
    }
}