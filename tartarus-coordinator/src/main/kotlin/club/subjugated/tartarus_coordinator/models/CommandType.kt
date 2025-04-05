package club.subjugated.tartarus_coordinator.models

enum class CommandType {
    UNSPECIFIED,
    ACCEPT_CONTRACT,
    UNLOCK,
    LOCK,
    RELEASE,
    ABORT,
    RESET
}
