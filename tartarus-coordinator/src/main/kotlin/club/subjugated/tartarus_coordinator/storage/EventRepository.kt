package club.subjugated.tartarus_coordinator.storage

import club.subjugated.tartarus_coordinator.models.Event
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EventRepository : JpaRepository<Event, Long> {
    fun findByContractIdAndCounter(contractId: Long, counter: Int): Event?

    fun findByContractIdOrderByCounterAsc(contractId: Long): List<Event>

    fun findBySessionTokenAndContractSerialNumberAndCounter(
        sessionToken: String,
        contractSerialNumber: Int,
        counter: Int,
    ): Event?

    fun findBySessionTokenOrderByCreatedAtDesc(sessionToken: String): List<Event>
}
