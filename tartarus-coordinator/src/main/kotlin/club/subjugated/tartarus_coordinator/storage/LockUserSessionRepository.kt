package club.subjugated.tartarus_coordinator.storage

import club.subjugated.tartarus_coordinator.models.LockUserSession
import org.springframework.data.jpa.repository.JpaRepository

interface LockUserSessionRepository : JpaRepository<LockUserSession, Long> {
    fun findByPublicKey(publicKey : String) : LockUserSession?
    fun findByName(name : String) : LockUserSession
    fun findByNonceUsed(nonce : String) : LockUserSession?
}