package club.subjugated.tartarus_coordinator.models

enum class CommandState {
    UNSPECIFIED,
    PENDING,
    ACKNOWLEDGED,
    ERROR,
}
