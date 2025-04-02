package club.subjugated.tartarus_coordinator.storage

import club.subjugated.tartarus_coordinator.models.LockSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface LockSessionRepository : JpaRepository<LockSession, Long> {
    fun findBySessionToken(sessionToken: String) : LockSession?

    fun findByShareTokenOrTotalControlToken(someToken: String, otherToken : String): LockSession?
}