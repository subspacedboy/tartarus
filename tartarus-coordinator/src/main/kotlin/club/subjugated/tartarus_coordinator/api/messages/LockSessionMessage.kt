package club.subjugated.tartarus_coordinator.api.messages

import club.subjugated.tartarus_coordinator.models.LockSession
import com.fasterxml.jackson.annotation.JsonFormat
import java.time.OffsetDateTime

data class LockSessionMessage(
    var name : String = "",
    var publicKey: String = "",
    var shareToken: String = "",
    var totalControlToken: String = "",
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) {
    companion object {
        fun fromLockSession(lockSession : LockSession) : LockSessionMessage {
            return LockSessionMessage(
                name = lockSession.name,
                publicKey = lockSession.publicKey,
                shareToken = lockSession.shareToken!!,
                totalControlToken = lockSession.totalControlToken!!,
                updatedAt = lockSession.updatedAt,
                createdAt = lockSession.createdAt
            )
        }
    }
}