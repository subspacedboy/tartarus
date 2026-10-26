package club.subjugated.overlord_exe.bots.superbot

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SuperBotSessionRepository : JpaRepository<SuperBotSession, Long> {
    fun findByName(name: String): SuperBotSession
    fun findByContractSerialNumber(serialNumber: Long): SuperBotSession
}
