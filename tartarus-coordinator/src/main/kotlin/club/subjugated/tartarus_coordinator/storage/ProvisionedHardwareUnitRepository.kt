package club.subjugated.tartarus_coordinator.storage

import club.subjugated.tartarus_coordinator.models.ProvisionedHardwareUnit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository

@Repository
interface ProvisionedHardwareUnitRepository :
    JpaRepository<ProvisionedHardwareUnit, String>,
    JpaSpecificationExecutor<ProvisionedHardwareUnit> {
    fun findByHardwareId(hardwareId: String): ProvisionedHardwareUnit?

    fun findByLastKnownSessionToken(sessionToken: String): List<ProvisionedHardwareUnit>

    fun findByInitialSessionToken(sessionToken: String): List<ProvisionedHardwareUnit>
}
