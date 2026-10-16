package club.subjugated.tartarus_coordinator.api.messages

data class LockStateMessage(val isLocked: Boolean, val emergencyUnlocked: Boolean = false) {}
