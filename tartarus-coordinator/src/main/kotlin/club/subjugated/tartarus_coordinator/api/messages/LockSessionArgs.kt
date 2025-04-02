package club.subjugated.tartarus_coordinator.api.messages

enum class LockSessionArgs {
    UNSPECIFIED,
    SUPPRESS_TC_TOKEN,
    SUPPRESS_LOCK_STATE,
    SUPPRESS_AVAILABLE_FOR_CONTRACT
}