package club.subjugated.tartarus_coordinator.storage

import club.subjugated.tartarus_coordinator.models.KnownToken
import club.subjugated.tartarus_coordinator.models.KnownTokenState
import org.springframework.data.jpa.repository.JpaRepository

interface KnownTokenRepository : JpaRepository<KnownToken, Long> {
    fun findByAuthorSessionIdAndShareableToken(authorId: Long, shareableToken: String): KnownToken?

    fun findByAuthorSessionIdAndState(
        authorSessionId: Long,
        state: KnownTokenState,
    ): List<KnownToken>

    fun findByAuthorSessionIdAndName(authorSessionId: Long, name : String) : KnownToken
}
