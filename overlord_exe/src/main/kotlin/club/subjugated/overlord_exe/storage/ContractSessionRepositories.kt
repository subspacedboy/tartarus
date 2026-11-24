package club.subjugated.overlord_exe.storage

import club.subjugated.overlord_exe.models.ContractSession
import club.subjugated.overlord_exe.models.SessionMachine
import club.subjugated.overlord_exe.models.SessionPhase
import org.springframework.data.jpa.repository.JpaRepository

interface ContractSessionRepository : JpaRepository<ContractSession, Long> {
    fun findByToken(token: String): ContractSession?
    fun findByContractId(contractId: Long): ContractSession?
    fun findByBotIdAndContractSerialNumber(botId: String, contractSerialNumber: Int): ContractSession?
    fun findBySubjectDidAndPhaseIn(subjectDid: String, phases: Collection<SessionPhase>): List<ContractSession>
    fun findByKeyholderDidAndPhaseIn(keyholderDid: String, phases: Collection<SessionPhase>): List<ContractSession>
    fun findByConvoIdAndPhaseIn(convoId: String, phases: Collection<SessionPhase>): List<ContractSession>
    fun findByBotIdAndKeyholderDidAndSubjectDidAndPhaseIn(
        botId: String,
        keyholderDid: String,
        subjectDid: String,
        phases: Collection<SessionPhase>,
    ): List<ContractSession>
    fun countBySubjectDidAndPhase(subjectDid: String, phase: SessionPhase): Long
}

interface SessionMachineRepository : JpaRepository<SessionMachine, Long> {
    fun findBySessionIdOrderByIdAsc(sessionId: Long): List<SessionMachine>
    fun findBySessionIdIn(sessionIds: Collection<Long>): List<SessionMachine>
    fun findByInfoToken(infoToken: String): SessionMachine?
    fun findByStatusToken(statusToken: String): SessionMachine?
}
