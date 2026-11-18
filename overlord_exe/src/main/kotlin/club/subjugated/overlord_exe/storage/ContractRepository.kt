package club.subjugated.overlord_exe.storage

import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ContractRepository : JpaRepository<Contract, Long> {
    fun findByBotNameAndSerialNumber(botName : String, serialNumber : Int) : Contract?
    fun findByBotNameAndStateIn(botName : String, state : List<ContractState>) : List<Contract>
    fun findByEmergencyUnlockedTrueAndStateIn(states: List<ContractState>): List<Contract>
    fun findByShareableTokenAndEmergencyUnlockedTrueAndStateIn(
        shareableToken: String,
        states: List<ContractState>,
    ): List<Contract>
}