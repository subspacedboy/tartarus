package club.subjugated.tartarus_coordinator.storage

import club.subjugated.tartarus_coordinator.models.AdminSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AdminSessionRepository : JpaRepository<AdminSession, Long> {
    fun findByName(name : String) : AdminSession
    fun findByPublicKey(name : String) : AdminSession?
}