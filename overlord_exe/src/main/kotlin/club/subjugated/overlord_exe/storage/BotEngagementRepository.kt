package club.subjugated.overlord_exe.storage

import club.subjugated.overlord_exe.models.BotEngagement
import club.subjugated.overlord_exe.models.EngagementPhase
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BotEngagementRepository : JpaRepository<BotEngagement, Long> {
    fun findByName(name: String): BotEngagement?
    fun findByInfoToken(infoToken: String): BotEngagement?
    fun findByStatusToken(statusToken: String): BotEngagement?
    fun findByBotIdAndContractSerialNumber(botId: String, contractSerialNumber: Int): BotEngagement?
    fun findByBotIdAndContractIdIn(botId: String, contractIds: List<Long>): List<BotEngagement>
    fun findByContractId(contractId: Long): BotEngagement?
    fun findByBotIdAndKeyholderDidAndSubjectDidAndPhaseIn(
        botId: String,
        keyholderDid: String,
        subjectDid: String,
        phases: List<EngagementPhase>,
    ): List<BotEngagement>
}
