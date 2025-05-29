package club.subjugated.overlord_exe.bots.bsky_selflock

import club.subjugated.overlord_exe.bots.timer_bot.TimerBotRecord
import org.springframework.data.jpa.repository.JpaRepository

interface BSkySelfLockRepository : JpaRepository<BSkySelfLockBotRecord, Long> {
    fun findByName(name : String) : BSkySelfLockBotRecord
    fun findByContractSerialNumber(serialNumber: Int): BSkySelfLockBotRecord
    fun findByContractIdIn(contractIds : List<Long>) : List<BSkySelfLockBotRecord>
}