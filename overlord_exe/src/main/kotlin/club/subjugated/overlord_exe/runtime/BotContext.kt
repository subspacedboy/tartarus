package club.subjugated.overlord_exe.runtime

import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractState
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.util.encodePublicKeySecp1
import club.subjugated.overlord_exe.util.loadECPublicKeyFromPkcs8
import org.slf4j.LoggerFactory
import java.security.interfaces.ECPublicKey
import java.util.Base64

/**
 * Scoped execution handle given to a single [OverlordBot].
 *
 * Replaces the multi-hop Spring ApplicationEvent chain (`IssueContract` -> `ContractService` -> `SendBotBytes` -> `BotComponent`)
 * with direct, synchronous, typed operations that persist and return [Contract] state and manage command counters automatically.
 */
interface BotContext {
    val botMap: BotMap
    val internalName: String get() = botMap.internalName
    val externalName: String get() = botMap.externalName

    fun issueContract(
        shareableToken: String,
        terms: String,
        public: Boolean = false,
        temporaryUnlock: Boolean = false,
        coBots: List<CoBotGrant> = emptyList(),
    ): Contract

    fun lock(contract: Contract): Contract
    fun unlock(contract: Contract): Contract
    fun release(contract: Contract): Contract
    fun addMessage(contract: Contract, message: String)
    fun syncContract(lockSession: String, serialNumber: Int): Contract
    fun observerGrant(botInternalName: String = "announcer"): CoBotGrant
    fun sendDm(convoId: String, message: String)
}

class DefaultBotContext(
    private val botMapProvider: () -> BotMap,
    private val botMapService: BotMapService,
    private val contractService: ContractService,
    private val coordinatorGateway: CoordinatorGateway,
    private val blueSkyService: BlueSkyService? = null,
) : BotContext {
    private val logger = LoggerFactory.getLogger(DefaultBotContext::class.java)

    override val botMap: BotMap
        get() = botMapProvider()

    override fun observerGrant(botInternalName: String): CoBotGrant {
        val targetBot = botMapService.getBotMap(botInternalName)
        val compressedPub = encodePublicKeySecp1(
            loadECPublicKeyFromPkcs8(targetBot.publicKey!!) as ECPublicKey
        )
        return CoBotGrant(
            botName = targetBot.externalName,
            compressedPublicKey = compressedPub,
            receiveEvents = true,
            canUnlock = false,
            canLock = false,
            canRelease = false,
        )
    }

    override fun issueContract(
        shareableToken: String,
        terms: String,
        public: Boolean,
        temporaryUnlock: Boolean,
        coBots: List<CoBotGrant>,
    ): Contract {
        val currentBotMap = botMap
        val compressedPublicKey = encodePublicKeySecp1(
            loadECPublicKeyFromPkcs8(currentBotMap.publicKey!!) as ECPublicKey
        )

        val effectiveCoBots = buildList {
            addAll(coBots)
            if (public && coBots.none { it.botName == botMapService.findBotMap("announcer")?.externalName }) {
                botMapService.findBotMap("announcer")?.let { announcer ->
                    val announcerPub = encodePublicKeySecp1(
                        loadECPublicKeyFromPkcs8(announcer.publicKey!!) as ECPublicKey
                    )
                    add(
                        CoBotGrant(
                            botName = announcer.externalName,
                            compressedPublicKey = announcerPub,
                            receiveEvents = true,
                        )
                    )
                }
            }
        }

        val wrapper = contractService.buildSignedContractCommand(
            botName = currentBotMap.externalName,
            terms = terms,
            temporaryUnlock = temporaryUnlock,
            privateKey = currentBotMap.privateKey!!,
            publicKey = compressedPublicKey,
            coBots = effectiveCoBots,
        )

        val signedMessageBase64 = Base64.getEncoder().encodeToString(wrapper.messageBytes)
        val botJwt = botMapService.getBotJwt(currentBotMap)

        val info = try {
            coordinatorGateway.createContract(
                botName = currentBotMap.externalName,
                botJwt = botJwt,
                shareableToken = shareableToken,
                signedMessageBase64 = signedMessageBase64,
                serialNumber = wrapper.contractSerialNumber,
            )
        } catch (e: Exception) {
            logger.error("Coordinator createContract failed for ${currentBotMap.internalName}: ${e.message}", e)
            null
        }

        val contract = contractService.getOrCreateContract(
            botName = currentBotMap.externalName,
            lockSessionToken = info?.lockSessionToken ?: "",
            serialNumber = wrapper.contractSerialNumber,
        )
        contract.shareableToken = shareableToken
        contract.signedMessage = wrapper.messageBytes
        if (info != null) {
            if (info.name.isNotBlank()) {
                contract.externalContractName = info.name
            }
            if (!info.state.isNullOrBlank()) {
                runCatching { ContractState.valueOf(info.state) }.getOrNull()?.let {
                    contract.state = it
                }
            } else if (contract.state == ContractState.UNSPECIFIED) {
                contract.state = ContractState.CREATED
            }
            contract.nextCounter = info.nextCounter
            contractService.recordCounter(contract, info.nextCounter)
        } else if (contract.state == ContractState.UNSPECIFIED) {
            contract.state = ContractState.CREATED
        }

        return contractService.save(contract)
    }

    private fun resolveCounterAndExternalName(contract: Contract, defaultCounter: Int = 1): Int {
        val currentBotMap = botMap
        val lockSession = contract.lockSessionToken
        if (!lockSession.isNullOrBlank()) {
            try {
                val botJwt = botMapService.getBotJwt(currentBotMap)
                val info = coordinatorGateway.getContractBySession(
                    botName = currentBotMap.externalName,
                    jwt = botJwt,
                    lockSession = lockSession,
                    serial = contract.serialNumber,
                )
                contractService.updateContractWithGetContractResponse(contract, info)
                if (info.nextCounter > 0) {
                    return info.nextCounter
                }
            } catch (e: Exception) {
                logger.debug("Using cached counter for contract ${contract.name} (${e.message})")
            }
        }
        val cached = contractService.getCounter(contract)
        return if (cached > 0) cached else defaultCounter
    }

    override fun lock(contract: Contract): Contract {
        val currentBotMap = botMap
        val counter = resolveCounterAndExternalName(contract, defaultCounter = 1)
        val contractName = contract.externalContractName ?: contract.name

        val lockBytes = contractService.makeLockCommand(
            botName = currentBotMap.externalName,
            contractName = contractName,
            contractSerialNumber = contract.serialNumber,
            counter = counter,
            privateKey = currentBotMap.privateKey!!,
        )
        val signedMessageBase64 = Base64.getEncoder().encodeToString(lockBytes)
        val botJwt = botMapService.getBotJwt(currentBotMap)

        coordinatorGateway.createCommand(
            botName = currentBotMap.externalName,
            botJwt = botJwt,
            contractName = contractName,
            shareableToken = contract.shareableToken,
            signedMessageBase64 = signedMessageBase64,
        )

        contractService.recordCounter(contract, counter + 1)
        return contract
    }

    override fun unlock(contract: Contract): Contract {
        val currentBotMap = botMap
        val counter = resolveCounterAndExternalName(contract, defaultCounter = 1)
        val contractName = contract.externalContractName ?: contract.name

        val unlockBytes = contractService.makeUnlockCommand(
            botName = currentBotMap.externalName,
            contractName = contractName,
            contractSerialNumber = contract.serialNumber,
            counter = counter,
            privateKey = currentBotMap.privateKey!!,
        )
        val signedMessageBase64 = Base64.getEncoder().encodeToString(unlockBytes)
        val botJwt = botMapService.getBotJwt(currentBotMap)

        coordinatorGateway.createCommand(
            botName = currentBotMap.externalName,
            botJwt = botJwt,
            contractName = contractName,
            shareableToken = contract.shareableToken,
            signedMessageBase64 = signedMessageBase64,
        )

        contractService.recordCounter(contract, counter + 1)
        return contract
    }

    override fun release(contract: Contract): Contract {
        val currentBotMap = botMap
        val counter = resolveCounterAndExternalName(contract, defaultCounter = 2000)
        val contractName = contract.externalContractName ?: contract.name

        val releaseBytes = contractService.makeReleaseCommand(
            botName = currentBotMap.externalName,
            contractName = contractName,
            contractSerialNumber = contract.serialNumber,
            privateKey = currentBotMap.privateKey!!,
            counter = counter,
        )
        val signedMessageBase64 = Base64.getEncoder().encodeToString(releaseBytes)
        val botJwt = botMapService.getBotJwt(currentBotMap)

        coordinatorGateway.createCommand(
            botName = currentBotMap.externalName,
            botJwt = botJwt,
            contractName = contractName,
            shareableToken = contract.shareableToken,
            signedMessageBase64 = signedMessageBase64,
        )

        contractService.recordCounter(contract, counter + 1)
        return contract
    }

    override fun addMessage(contract: Contract, message: String) {
        val currentBotMap = botMap
        if (contract.externalContractName.isNullOrBlank() && !contract.lockSessionToken.isNullOrBlank()) {
            resolveCounterAndExternalName(contract)
        }
        val contractName = contract.externalContractName ?: contract.name
        val botJwt = botMapService.getBotJwt(currentBotMap)

        coordinatorGateway.addMessage(
            botName = currentBotMap.externalName,
            botJwt = botJwt,
            contractName = contractName,
            message = message,
        )
    }

    override fun syncContract(lockSession: String, serialNumber: Int): Contract {
        val currentBotMap = botMap
        val botJwt = botMapService.getBotJwt(currentBotMap)
        val info = coordinatorGateway.getContractBySession(
            botName = currentBotMap.externalName,
            jwt = botJwt,
            lockSession = lockSession,
            serial = serialNumber,
        )
        val contract = contractService.getOrCreateContract(
            botName = currentBotMap.externalName,
            lockSessionToken = lockSession,
            serialNumber = serialNumber,
        )
        return contractService.updateContractWithGetContractResponse(contract, info)
    }

    override fun sendDm(convoId: String, message: String) {
        if (convoId.isNotBlank() && message.isNotBlank()) {
            blueSkyService?.sendDm(convoId, message)
        }
    }
}
