package club.subjugated.tartarus_coordinator.api.messages

import club.subjugated.tartarus_coordinator.models.LockUserSession

data class LockUserSessionMessage(
    var name : String? = "",
    var lockSession : LockSessionMessage? = null
) {
    companion object {
        fun fromLockUserSession(lockUserSession: LockUserSession): LockUserSessionMessage {
            return LockUserSessionMessage(
                name = lockUserSession.name,
                lockSession = LockSessionMessage.fromLockSession(lockUserSession.lockSession, null, null, listOf())
            )
        }
    }
}