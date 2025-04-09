package club.subjugated.overlord_exe.storage

import club.subjugated.overlord_exe.models.BotMap
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BotMapRepository : JpaRepository<BotMap, Long> {
    fun findByInternalNameAndCoordinator(name : String, coordinator : String) : BotMap?
}