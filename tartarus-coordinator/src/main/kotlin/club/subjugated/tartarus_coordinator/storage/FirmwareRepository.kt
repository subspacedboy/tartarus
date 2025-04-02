package club.subjugated.tartarus_coordinator.storage

import club.subjugated.tartarus_coordinator.models.Firmware
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FirmwareRepository : JpaRepository<Firmware, Long> {
    fun findFirstByOrderByCreatedAtDesc() : Firmware
}