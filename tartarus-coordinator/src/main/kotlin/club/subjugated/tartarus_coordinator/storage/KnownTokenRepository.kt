package club.subjugated.tartarus_coordinator.storage

import club.subjugated.tartarus_coordinator.models.KnownToken
import org.springframework.data.jpa.repository.JpaRepository

interface KnownTokenRepository : JpaRepository<KnownToken, Long> {
    fun findByAuthorSessionIdAndShareableToken(authorId: Long, shareableToken: String) : KnownToken?
}