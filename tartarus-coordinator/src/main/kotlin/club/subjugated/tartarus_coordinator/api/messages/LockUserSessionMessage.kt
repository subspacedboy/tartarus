package club.subjugated.tartarus_coordinator.api.messages

import club.subjugated.tartarus_coordinator.models.LockUserSession

data class LockUserSessionMessage(
    var name : String? = "",
) {
    companion object {
        fun fromLockUserSession(lockUserSession: LockUserSession): LockUserSessionMessage {
            return LockUserSessionMessage(
                name = lockUserSession.name
            )
        }
    }
}