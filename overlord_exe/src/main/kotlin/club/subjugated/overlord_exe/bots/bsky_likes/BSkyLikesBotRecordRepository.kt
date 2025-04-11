package club.subjugated.overlord_exe.bots.bsky_likes

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BSkyLikesBotRecordRepository : JpaRepository<BSkyLikeBotRecord, Long> {
    fun findByContractId(contractId : Long) : BSkyLikeBotRecord
    fun findByContractIdIn(contractIds : List<Long>) : List<BSkyLikeBotRecord>
    fun findByContractSerialNumber(contractSerialNumber : Int) : BSkyLikeBotRecord
}