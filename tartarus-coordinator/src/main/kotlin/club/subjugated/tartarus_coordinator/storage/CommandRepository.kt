package club.subjugated.tartarus_coordinator.storage

import club.subjugated.tartarus_coordinator.models.Command
import club.subjugated.tartarus_coordinator.models.CommandState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CommandRepository : JpaRepository<Command, Long> {
    fun findByCommandQueueIdAndStateOrderByCreatedAt(
        commandQueueId: Long,
        state: CommandState,
    ): List<Command>

    fun findByCommandQueueIdAndSerialNumber(commandQueueId: Long, serialNumber: Int): List<Command>

    fun findByAuthorSessionIdAndContractIdOrderByCounterDesc(authorSessionId: Long, contractId: Long): List<Command>
    fun findByContractIdOrderByCounterDesc(contractId: Long): List<Command>
}
