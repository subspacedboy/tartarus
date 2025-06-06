package club.subjugated.overlord_exe.statemachines.bsky_crowd_time

enum class BSkyCrowdTimeState {
    UNSPECIFIED, CREATED, NEEDS_INFO, WAITING, OPEN_POSTED, IN_OPEN, OPEN_CLOSED, COMPLETE
}