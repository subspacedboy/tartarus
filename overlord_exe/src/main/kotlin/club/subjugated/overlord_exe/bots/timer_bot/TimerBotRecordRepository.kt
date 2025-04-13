package club.subjugated.overlord_exe.bots.timer_bot

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TimerBotRecordRepository : JpaRepository<TimerBotRecord, Long> {
//    fun findByContractId(contractId : Long) : TimerBotRecord
    fun findByContractIdIn(contractIds : List<Long>) : List<TimerBotRecord>
    fun findByContractSerialNumber(serialNumber: Long) : TimerBotRecord
}