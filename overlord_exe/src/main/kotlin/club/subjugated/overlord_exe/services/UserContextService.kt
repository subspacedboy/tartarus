package club.subjugated.overlord_exe.services

import club.subjugated.overlord_exe.models.ContractSession
import club.subjugated.overlord_exe.models.SessionPhase
import club.subjugated.overlord_exe.models.UserContext
import club.subjugated.overlord_exe.models.UserSessionSummary
import club.subjugated.overlord_exe.storage.ContractRepository
import club.subjugated.overlord_exe.storage.ContractSessionRepository
import club.subjugated.overlord_exe.storage.SessionMachineRepository
import org.springframework.stereotype.Service

@Service
class UserContextService(
    private val userProfileService: UserProfileService,
    private val sessionRepository: ContractSessionRepository,
    private val machineRepository: SessionMachineRepository,
    private val contractRepository: ContractRepository,
) {
    companion object {
        val ACTIVE_PHASES = listOf(SessionPhase.CREATED, SessionPhase.ISSUED, SessionPhase.ACTIVE)
    }

    fun buildContextForDid(did: String): UserContext {
        val profile = userProfileService.findOrCreateByDid(did)
        val tokens = userProfileService.getTokensForUser(did)
        val subjectSessions = sessionRepository.findBySubjectDidAndPhaseIn(did, ACTIVE_PHASES)
        val keyholderSessions = sessionRepository.findByKeyholderDidAndPhaseIn(did, ACTIVE_PHASES)
        val completedCount = sessionRepository.countBySubjectDidAndPhase(did, SessionPhase.COMPLETE)

        return UserContext(
            profile = profile,
            knownTokens = tokens,
            activeAsSubject = summarize(subjectSessions),
            activeAsKeyholder = summarize(keyholderSessions),
            completedSessionCount = completedCount,
        )
    }

    fun buildContextForHandle(handle: String): UserContext? {
        val profile = userProfileService.findByHandle(handle) ?: return null
        return buildContextForDid(profile.did)
    }

    private fun summarize(sessions: List<ContractSession>): List<UserSessionSummary> {
        if (sessions.isEmpty()) return emptyList()
        val machinesBySession = machineRepository.findBySessionIdIn(sessions.map { it.id })
            .groupBy { it.sessionId }
        return sessions.map { session ->
            val contract = session.contractId?.let { contractRepository.findById(it).orElse(null) }
            val subjectHandle = userProfileService.findByDid(session.subjectDid)?.handle
            UserSessionSummary(
                session = session,
                machines = machinesBySession[session.id].orEmpty(),
                contract = contract,
                subjectHandle = subjectHandle,
            )
        }
    }
}
