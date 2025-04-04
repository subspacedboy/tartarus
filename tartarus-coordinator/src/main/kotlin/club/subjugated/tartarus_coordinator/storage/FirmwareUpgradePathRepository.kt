package club.subjugated.tartarus_coordinator.storage

import club.subjugated.tartarus_coordinator.models.FirmwareUpgradePath
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FirmwareUpgradePathRepository : JpaRepository<FirmwareUpgradePath, Long> {
    fun findByFromVersion(fromVersion : String) : List<FirmwareUpgradePath>
}