package club.subjugated.overlord_exe.bots.timer_bot

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TimerSessionRepository : JpaRepository<TimerSession, Long> {
    fun findByName(name: String): TimerSession
    fun findByContractIdIn(contractIds: List<Long>): List<TimerSession>
    fun findByContractSerialNumber(serialNumber: Long): TimerSession
}
