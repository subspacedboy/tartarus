package club.subjugated.tartarus_coordinator.storage

import club.subjugated.tartarus_coordinator.models.CommandQueue
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CommandQueueRepository : JpaRepository<CommandQueue, Long> {
}