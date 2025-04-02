package club.subjugated.tartarus_coordinator.api.messages

import club.subjugated.tartarus_coordinator.models.KnownToken
import com.fasterxml.jackson.annotation.JsonFormat
import java.time.OffsetDateTime

data class KnownTokenMessage(
    var name: String? = "",
    var state: String? = "",
    var notes: String? = "",
    var shareableToken: String? = "",
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
) {
    companion object {
        fun fromKnownToken(knownToken: KnownToken): KnownTokenMessage {
            return KnownTokenMessage(
                name = knownToken.name,
                state = knownToken.state.toString(),
                notes = knownToken.notes,
                shareableToken = knownToken.shareableToken,
                createdAt = knownToken.createdAt
            )
        }
    }
}
