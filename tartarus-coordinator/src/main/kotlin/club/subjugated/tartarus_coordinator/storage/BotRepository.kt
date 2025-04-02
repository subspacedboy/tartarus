package club.subjugated.tartarus_coordinator.storage

import club.subjugated.tartarus_coordinator.models.Bot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BotRepository : JpaRepository<Bot, Long> {
    fun findByName(name : String) : Bot
}