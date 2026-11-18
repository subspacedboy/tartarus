package club.subjugated.overlord_exe.services

import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.models.BotEngagement
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.EngagementPhase
import club.subjugated.overlord_exe.storage.BotEngagementRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class EmergencyRelockService(
    private val contractService: ContractService,
    private val botEngagementRepository: BotEngagementRepository,
    private val botComponent: BotComponent,
) {
    private val logger = LoggerFactory.getLogger(EmergencyRelockService::class.java)

    fun findEmergencyUnlockedContractsForUser(
        subjectDid: String,
        convoId: String,
        shareableToken: String?,
    ): List<Contract> {
        val candidates = mutableListOf<Contract>()

        if (!shareableToken.isNullOrBlank()) {
            candidates += contractService.findEmergencyUnlockedContractsForShareableToken(shareableToken)
        }

        val engagements = buildList {
            if (subjectDid.isNotBlank()) {
                addAll(botEngagementRepository.findBySubjectDidAndPhase(subjectDid, EngagementPhase.ACTIVE))
            }
            if (convoId.isNotBlank()) {
                addAll(botEngagementRepository.findByConvoIdAndPhase(convoId, EngagementPhase.ACTIVE))
            }
        }.distinctBy { it.id to it.name }

        for (engagement in engagements) {
            resolveContractForEngagement(engagement)?.let { candidates += it }
            val engToken = engagement.shareableToken
            if (!engToken.isNullOrBlank()) {
                candidates += contractService.findEmergencyUnlockedContractsForShareableToken(engToken)
            }
        }

        return candidates
            .filter { it.emergencyUnlocked && it.state in ContractService.LIVE_STATES }
            .distinctBy { if (it.id != 0L) it.id else "${it.botName}:${it.serialNumber}" }
    }

    private fun resolveContractForEngagement(engagement: BotEngagement): Contract? {
        engagement.contractId?.let { id ->
            contractService.findById(id)?.let { return it }
        }
        if (engagement.contractSerialNumber > 0) {
            val externalName = botComponent.handlers.keys
                .find { it.internalName == engagement.botId }
                ?.externalName
            if (externalName != null) {
                return contractService.findContract(externalName, engagement.contractSerialNumber)
            }
        }
        return null
    }

    fun relockForUser(ctx: ConversationContext): String {
        val subjectDid = ctx.bskyUser?.did ?: ctx.currentMessage.sender.did
        val shareableToken = ctx.bskyUser?.shareableToken

        val contracts = findEmergencyUnlockedContractsForUser(
            subjectDid = subjectDid,
            convoId = ctx.convoId,
            shareableToken = shareableToken,
        )

        if (contracts.isEmpty()) {
            return "No emergency-unlocked active contracts found for your account."
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
