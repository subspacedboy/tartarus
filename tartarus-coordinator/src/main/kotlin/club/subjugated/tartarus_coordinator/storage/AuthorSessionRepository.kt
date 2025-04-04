package club.subjugated.tartarus_coordinator.storage

import club.subjugated.tartarus_coordinator.models.AuthorSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AuthorSessionRepository : JpaRepository<AuthorSession, Long> {
    fun findByName(name: String): AuthorSession

    fun findByPublicKey(publicKey: String): AuthorSession?
}
