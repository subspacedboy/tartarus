package club.subjugated.tartarus_coordinator.events

import club.subjugated.tartarus_coordinator.models.Contract
import club.subjugated.tartarus_coordinator.models.LockSession

class PeriodicUpdateEvent(val source: Any, val lockSession: LockSession, val contract: Contract?)
