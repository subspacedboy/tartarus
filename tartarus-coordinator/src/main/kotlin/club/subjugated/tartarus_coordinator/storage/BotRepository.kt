package club.subjugated.tartarus_coordinator.storage

import club.subjugated.tartarus_coordinator.models.Bot
import club.subjugated.tartarus_coordinator.models.BotState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BotRepository : JpaRepository<Bot, Long> {
    fun findByName(name: String): Bot

    fun findByPublicKey(publicKey: String): Bot?

    fun findByAuthorSessionIdOrderByCreatedAtDesc(authorSessionId: Long): List<Bot>

    fun findByAuthorSessionIdAndInternalName(authorSessionId: Long, internalName: String): Bot?

    fun findByStateOrderByCreatedAtDesc(state: BotState): List<Bot>
}
