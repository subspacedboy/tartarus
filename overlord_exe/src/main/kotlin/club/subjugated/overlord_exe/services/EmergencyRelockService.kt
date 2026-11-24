package club.subjugated.overlord_exe.services

import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractSession
import club.subjugated.overlord_exe.models.ContractState
import club.subjugated.overlord_exe.models.SessionPhase
import club.subjugated.overlord_exe.storage.ContractSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class EmergencyRelockService(
    private val contractService: ContractService,
    private val sessionRepository: ContractSessionRepository,
    private val botComponent: BotComponent,
) {
    private val logger = LoggerFactory.getLogger(EmergencyRelockService::class.java)

    companion object {
        private val CANDIDATE_SESSION_PHASES = listOf(SessionPhase.ACTIVE, SessionPhase.ISSUED)
        private val TERMINAL_CONTRACT_STATES = setOf(
            ContractState.RELEASED,
            ContractState.ABORTED,
            ContractState.REJECTED,
        )
    }

    fun findEmergencyUnlockedContractsForUser(
        subjectDid: String,
        convoId: String,
        shareableTokens: List<String> = emptyList(),
    ): List<Contract> {
        val candidates = mutableListOf<Contract>()

        shareableTokens.filter { it.isNotBlank() }.forEach { token ->
            candidates += contractService.findContractsForShareableToken(token)
        }

        val sessions = buildList {
            if (subjectDid.isNotBlank()) {
                addAll(sessionRepository.findBySubjectDidAndPhaseIn(subjectDid, CANDIDATE_SESSION_PHASES))
            }
            if (convoId.isNotBlank()) {
                addAll(sessionRepository.findByConvoIdAndPhaseIn(convoId, CANDIDATE_SESSION_PHASES))
            }
        }.distinctBy { it.id to it.token }

        for (session in sessions) {
            resolveContractForSession(session)?.let { candidates += it }
            val sessionToken = session.shareableToken
            if (!sessionToken.isNullOrBlank()) {
                candidates += contractService.findContractsForShareableToken(sessionToken)
            }
        }

        val uniqueCandidates = candidates
            .distinctBy { if (it.id != 0L) it.id else "${it.botName}:${it.serialNumber}" }
            .filter { it.state !in TERMINAL_CONTRACT_STATES }

        if (uniqueCandidates.isEmpty()) {
            logger.info("No non-terminal candidate contracts found for subjectDid=$subjectDid convoId=$convoId")
            return emptyList()
        }

        val synced = uniqueCandidates.map { candidate ->
            val lockSession = candidate.lockSessionToken
            val botCtx = botComponent.contextForExternalName(candidate.botName)
            if (!lockSession.isNullOrBlank() && botCtx != null) {
                runCatching {
                    botCtx.syncContract(lockSession, candidate.serialNumber)
                }.onFailure { ex ->
                    logger.warn("Failed to force-sync candidate contract ${candidate.name}: ${ex.message}")
                }.getOrDefault(candidate)
            } else {
                candidate
            }
        }

        val live = synced.filter { it.state in ContractService.LIVE_STATES }
        if (live.isEmpty()) {
            return emptyList()
        }

        val emergencyOnly = live.filter { it.emergencyUnlocked }
        return emergencyOnly.ifEmpty { live }
    }

    private fun resolveContractForSession(session: ContractSession): Contract? {
        session.contractId?.let { id ->
            contractService.findById(id)?.let { return it }
        }
        if (session.contractSerialNumber > 0) {
            val externalName = botComponent.handlers.keys
                .find { it.internalName == session.botId }
                ?.externalName
            if (externalName != null) {
                return contractService.findContract(externalName, session.contractSerialNumber)
            }
        }
        return null
    }

    fun relockForUser(ctx: ConversationContext): String {
        val subjectDid = ctx.userContext?.did ?: ctx.currentMessage.sender.did
        val knownTokens = ctx.userContext?.knownTokens?.map { it.token }.orEmpty()

        val contracts = findEmergencyUnlockedContractsForUser(
            subjectDid = subjectDid,
            convoId = ctx.convoId,
            shareableTokens = knownTokens,
        )

        if (contracts.isEmpty()) {
            return "No active contracts found for your account."
        }

        val relocked = mutableListOf<String>()
        val errors = mutableListOf<String>()

        for (contract in contracts) {
            val botCtx = botComponent.contextForExternalName(contract.botName)
            if (botCtx == null) {
                errors += "${contract.externalContractName ?: contract.name}: bot runtime unavailable"
                continue
            }
            runCatching {
                botCtx.lock(contract)
            }.onSuccess {
                relocked += contract.externalContractName ?: contract.name
            }.onFailure { ex ->
                logger.warn("Failed to relock contract ${contract.name} (${contract.botName}): ${ex.message}", ex)
                errors += "${contract.externalContractName ?: contract.name}: ${ex.message ?: "unknown error"}"
            }
        }

        return when {
            errors.isEmpty() ->
                "Sent relock command for ${relocked.size} contract(s). Your device should relock shortly and resume normal operation."
            relocked.isEmpty() ->
                "Failed to relock: ${errors.joinToString("; ")}"
            else ->
                "Sent relock command for ${relocked.size} contract(s), but ${errors.size} failed: ${errors.joinToString("; ")}"
        }
    }
}
