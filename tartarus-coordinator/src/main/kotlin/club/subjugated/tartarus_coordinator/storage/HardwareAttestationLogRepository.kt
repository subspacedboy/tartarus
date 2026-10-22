package club.subjugated.tartarus_coordinator.storage

import club.subjugated.tartarus_coordinator.models.HardwareAttestationLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface HardwareAttestationLogRepository : JpaRepository<HardwareAttestationLog, Long> {
    fun findTop50ByHardwareIdOrderByCreatedAtDesc(hardwareId: String): List<HardwareAttestationLog>

    fun findByHardwareIdOrderByCreatedAtDesc(hardwareId: String): List<HardwareAttestationLog>

    fun findBySessionTokenOrderByCreatedAtDesc(sessionToken: String): List<HardwareAttestationLog>
}
