package club.subjugated.tartarus_coordinator.storage

import club.subjugated.tartarus_coordinator.models.Contract
import club.subjugated.tartarus_coordinator.models.ContractState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ContractRepository : JpaRepository<Contract, Long> {
    fun findByAuthorSessionIdAndStateOrderByCreatedAtDesc(authorSessionId: Long, state: ContractState) : List<Contract>
    fun findByAuthorSessionIdAndShareableTokenOrderByCreatedAtDesc(authorSessionId: Long, shareableToken: String): List<Contract>
    fun findByName(name: String): Contract

    fun findByLockSessionIdOrderByCreatedAtDesc(lockSessionId: Long): List<Contract>
    fun findByLockSessionIdAndSerialNumber(lockSessionId: Long, serial : Int): Contract
    fun findByLockSessionIdAndStateInOrderByCreatedAtDesc(lockSessionId: Long, states: List<ContractState>): List<Contract>
}
