package club.subjugated.tartarus_coordinator.storage

import club.subjugated.tartarus_coordinator.models.SafetyKey
import club.subjugated.tartarus_coordinator.models.SafetyKeyState
import org.springframework.data.jpa.repository.JpaRepository

interface SafetyKeyRepository : JpaRepository<SafetyKey, Long> {
    fun getAllByState(state: SafetyKeyState): List<SafetyKey>
}
