package club.subjugated.overlord_exe.bots.superbot

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SuperBotRecordRepository : JpaRepository<SuperBotRecord, Long> {
    fun findByName(name : String) : SuperBotRecord
    fun findByContractSerialNumber(serialNumber : Long) : SuperBotRecord
}