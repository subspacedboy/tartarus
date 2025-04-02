package club.subjugated.tartarus_coordinator.storage

import club.subjugated.tartarus_coordinator.models.Contract
import club.subjugated.tartarus_coordinator.models.ContractState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ContractRepository : JpaRepository<Contract, Long> {
    fun findByShareableTokenAndStateOrderByCreatedAt(shareableToken : String, contractState: ContractState) : List<Contract>
}