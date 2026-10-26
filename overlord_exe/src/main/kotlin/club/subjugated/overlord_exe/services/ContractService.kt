package club.subjugated.overlord_exe.services

import club.subjugated.fb.message.Bot
import club.subjugated.fb.message.LockCommand
import club.subjugated.fb.message.Permission
import club.subjugated.fb.message.ReleaseCommand
import club.subjugated.fb.message.SignedMessage
import club.subjugated.fb.message.UnlockCommand
import club.subjugated.overlord_exe.events.AddMessageToContract
import club.subjugated.overlord_exe.events.BotRestAction
import club.subjugated.overlord_exe.events.IssueContract
import club.subjugated.overlord_exe.events.IssueLock
import club.subjugated.overlord_exe.events.IssueRelease
import club.subjugated.overlord_exe.events.IssueUnlock
import club.subjugated.overlord_exe.events.SendBotBytes
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractState
import club.subjugated.overlord_exe.runtime.CoBotGrant
import club.subjugated.overlord_exe.storage.ContractRepository
import club.subjugated.overlord_exe.util.ContractCommandWrapper
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.derToRawSignature
import club.subjugated.overlord_exe.util.encodePublicKeySecp1
import club.subjugated.overlord_exe.util.loadECPublicKeyFromPkcs8
import club.subjugated.overlord_exe.util.loadPrivateKeyFromPkcs8Bytes
import club.subjugated.overlord_exe.util.signDoubleSha256Raw
import com.google.flatbuffers.FlatBufferBuilder
import kotlinx.serialization.Serializable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.security.interfaces.ECPublicKey
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

@Serializable
data class BotContractInfo(
    val name: String,
    val shareableToken: String? = null,
    val state: String? = null,
    val authorSessionName: String? = null,
    val body: String? = null,
    val nextCounter: Int = 0,
    val serialNumber: Int? = null,
    val lockSessionToken: String? = null,
)

@Service
class ContractService(
    private var contractRepository: ContractRepository,
    private var botMapService: BotMapService,
    private var timeSource: TimeSource,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val environment: Environment,
    private val logger: Logger = LoggerFactory.getLogger(ContractService::class.java),
) {
    private val contractCounters = ConcurrentHashMap<String, Int>()

    private fun counterKey(botName: String, serialNumber: Int): String = "$botName:$serialNumber"

    fun recordCounter(contract: Contract, counter: Int) {
        contract.nextCounter = counter
        contractCounters[counterKey(contract.botName, contract.serialNumber)] = counter
    }

    fun getCounter(contract: Contract): Int {
        if (contract.nextCounter > 0) {
            return contract.nextCounter
        }
        return contractCounters[counterKey(contract.botName, contract.serialNumber)] ?: 0
    }

    private fun prepareForPersistence(contract: Contract) {
        if (contract.nextCounter > 0) {
            contractCounters[counterKey(contract.botName, contract.serialNumber)] = contract.nextCounter
        }
        val token = contract.shareableToken
        if (token != null && token.length > 10) {
            contract.shareableToken = token.take(10)
        }
    }

    private fun hydrateCounter(contract: Contract): Contract {
        if (contract.nextCounter == 0) {
            contractCounters[counterKey(contract.botName, contract.serialNumber)]?.let {
                contract.nextCounter = it
            }
        } else {
            contractCounters[counterKey(contract.botName, contract.serialNumber)] = contract.nextCounter
        }
        return contract
    }

    fun getLiveContractsForBot(botName: String): List<Contract> {
        return contractRepository.findByBotNameAndStateIn(
            botName,
            listOf(ContractState.ACCEPTED, ContractState.CONFIRMED),
        ).map { hydrateCounter(it) }
    }

    fun getOrCreateContract(
        botName: String,
        lockSessionToken: String,
        serialNumber: Int,
    ): Contract {
        val existing = contractRepository.findByBotNameAndSerialNumber(botName, serialNumber)
        if (existing != null) {
            if (existing.lockSessionToken.isNullOrBlank() && lockSessionToken.isNotBlank()) {
                existing.lockSessionToken = lockSessionToken
                contractRepository.save(existing)
            }
            return hydrateCounter(existing)
        }
        return createContract(botName, lockSessionToken, serialNumber)
    }

    fun findContract(botName: String, serialNumber: Int): Contract? {
        return contractRepository.findByBotNameAndSerialNumber(botName, serialNumber)?.let {
            hydrateCounter(it)
        }
    }

    fun getContract(botName: String, serialNumber: Int): Contract {
        return hydrateCounter(contractRepository.findByBotNameAndSerialNumber(botName, serialNumber)!!)
    }

    fun markReleased(botName: String, serialNumber: Int): Contract {
        val contract = getContract(botName, serialNumber)
        contract.state = ContractState.RELEASED
        contract.updatedAt = timeSource.nowInUtc()
        contractRepository.save(contract)
        return contract
    }

    fun save(contract: Contract): Contract {
        prepareForPersistence(contract)
        contract.updatedAt = timeSource.nowInUtc()
        return hydrateCounter(contractRepository.save(contract))
    }

    fun updateContractWithGetContractResponse(
        contract: Contract,
        response: BotContractInfo,
    ): Contract {
        response.body?.let { contract.signedMessage = Base64.getDecoder().decode(it) }
        response.state?.let { stateStr ->
            runCatching { ContractState.valueOf(stateStr) }.getOrNull()?.let {
                contract.state = it
            }
        }
        if (response.name.isNotBlank()) {
            contract.externalContractName = response.name
        }
        if (!response.shareableToken.isNullOrBlank()) {
            contract.shareableToken = response.shareableToken
        }
        if (!response.lockSessionToken.isNullOrBlank()) {
            contract.lockSessionToken = response.lockSessionToken
        }
        contract.nextCounter = response.nextCounter
        recordCounter(contract, response.nextCounter)
        prepareForPersistence(contract)
        contract.updatedAt = timeSource.nowInUtc()
        contractRepository.save(contract)
        return contract
    }

    @EventListener
    fun handleIssueContractRequest(event: IssueContract) {
        val compressedPublicKey = encodePublicKeySecp1(
            loadECPublicKeyFromPkcs8(event.botMap.publicKey!!) as ECPublicKey
        )
        val wrapper = makeCreateContractCommand(
            event.botMap.externalName,
            event.shareableToken,
            event.terms,
            false,
            event.botMap.privateKey!!,
            compressedPublicKey,
            event.public,
        )

        event.serialNumberRecorder(wrapper.contractSerialNumber)

        val signedMessageBase64 = Base64.getEncoder().encodeToString(wrapper.messageBytes)
        applicationEventPublisher.publishEvent(
            SendBotBytes(
                source = this,
                botMap = event.botMap,
                action = BotRestAction.CreateContract(
                    shareableToken = event.shareableToken,
                    signedMessageBase64 = signedMessageBase64,
                ),
            )
        )
    }

    @EventListener
    fun handleAddMessageRequest(event: AddMessageToContract) {
        applicationEventPublisher.publishEvent(
            SendBotBytes(
                source = this,
                botMap = event.botMap,
                action = BotRestAction.CreateMessage(
                    contractName = event.contract.externalContractName ?: event.contract.name,
                    message = event.message,
                ),
            )
        )
    }

    @EventListener
    fun handleIssueRelease(event: IssueRelease) {
        logger.info("Issuing Release")
        if (environment.activeProfiles.contains("test")) {
            logger.info("Test environment. Early escape from issue.")
            return
        }

        val counter = getCounter(event.contract).let { if (it > 0) it else 2000 }
        val releaseCommandBytes = makeReleaseCommand(
            event.botMap.externalName,
            event.contract.externalContractName ?: event.contract.name,
            event.contract.serialNumber,
            event.botMap.privateKey!!,
            counter,
        )
        val signedMessageBase64 = Base64.getEncoder().encodeToString(releaseCommandBytes)
        applicationEventPublisher.publishEvent(
            SendBotBytes(
                source = this,
                botMap = event.botMap,
                action = BotRestAction.CreateCommand(
                    contractName = event.contract.externalContractName ?: event.contract.name,
                    shareableToken = event.contract.shareableToken,
                    signedMessageBase64 = signedMessageBase64,
                ),
            )
        )
    }

    @EventListener
    fun handleIssueUnlock(event: IssueUnlock) {
        if (environment.activeProfiles.contains("test")) return

        val counter = getCounter(event.contract).let { if (it > 0) it else 1 }
        val unlockCommandBytes = makeUnlockCommand(
            event.botMap.externalName,
            event.contract.externalContractName ?: event.contract.name,
            event.contract.serialNumber,
            counter,
            event.botMap.privateKey!!,
        )
        val signedMessageBase64 = Base64.getEncoder().encodeToString(unlockCommandBytes)
        applicationEventPublisher.publishEvent(
            SendBotBytes(
                source = this,
                botMap = event.botMap,
                action = BotRestAction.CreateCommand(
                    contractName = event.contract.externalContractName ?: event.contract.name,
                    shareableToken = event.contract.shareableToken,
                    signedMessageBase64 = signedMessageBase64,
                ),
            )
        )
    }

    @EventListener
    fun handleIssueLock(event: IssueLock) {
        if (environment.activeProfiles.contains("test")) return

        val counter = getCounter(event.contract).let { if (it > 0) it else 1 }
        val lockCommandBytes = makeLockCommand(
            event.botMap.externalName,
            event.contract.externalContractName ?: event.contract.name,
            event.contract.serialNumber,
            counter,
            event.botMap.privateKey!!,
        )
        val signedMessageBase64 = Base64.getEncoder().encodeToString(lockCommandBytes)
        applicationEventPublisher.publishEvent(
            SendBotBytes(
                source = this,
                botMap = event.botMap,
                action = BotRestAction.CreateCommand(
                    contractName = event.contract.externalContractName ?: event.contract.name,
                    shareableToken = event.contract.shareableToken,
                    signedMessageBase64 = signedMessageBase64,
                ),
            )
        )
    }

    private fun extractFlatBufferTableSlice(sizedBytes: ByteArray): ByteArray {
        val start = sizedBytes[0].toInt() and 0xFF
        val tableStart = sizedBytes[start].toInt() and 0xFF
        val vtableStart = start - tableStart
        return sizedBytes.copyOfRange(vtableStart, sizedBytes.size)
    }

    private fun signFlatBufferTable(sizedBytes: ByteArray, privateKeyBytes: ByteArray): ByteArray {
        val payloadToHash = extractFlatBufferTableSlice(sizedBytes)
        val ecPrivateKey = loadPrivateKeyFromPkcs8Bytes(privateKeyBytes)
        return signDoubleSha256Raw(ecPrivateKey, payloadToHash)
    }

    fun makeReleaseCommand(
        botName: String,
        contractName: String,
        contractSerialNumber: Int,
        privateKey: ByteArray,
        counter: Int = 2000,
    ): ByteArray {
        val builder = FlatBufferBuilder(1024)

        val serialNumber = Random.nextInt(0, 1 shl 16)
        ReleaseCommand.startReleaseCommand(builder)
        ReleaseCommand.addContractSerialNumber(builder, contractSerialNumber.toUShort())
        ReleaseCommand.addCounter(builder, counter.toUShort())
        ReleaseCommand.addSerialNumber(builder, serialNumber.toUShort())
        val releaseOffset = ReleaseCommand.endReleaseCommand(builder)

        builder.finish(releaseOffset)
        val signature = signFlatBufferTable(builder.sizedByteArray(), privateKey)

        val signatureOffset = builder.createByteVector(signature)
        val botAuthorityIdentifier = builder.createString(botName)

        SignedMessage.startSignedMessage(builder)
        SignedMessage.addSignature(builder, signatureOffset)
        SignedMessage.addPayload(builder, releaseOffset)
        SignedMessage.addPayloadType(
            builder,
            club.subjugated.fb.message.MessagePayload.ReleaseCommand,
        )
        SignedMessage.addAuthorityIdentifier(builder, botAuthorityIdentifier)
        val signedMessageOffset = SignedMessage.endSignedMessage(builder)

        builder.finish(signedMessageOffset)
        return builder.sizedByteArray()
    }

    fun makeUnlockCommand(
        botName: String,
        contractName: String,
        contractSerialNumber: Int,
        counter: Int,
        privateKey: ByteArray,
    ): ByteArray {
        val builder = FlatBufferBuilder(1024)

        val serialNumber = Random.nextInt(0, 1 shl 16)
        UnlockCommand.startUnlockCommand(builder)
        UnlockCommand.addContractSerialNumber(builder, contractSerialNumber.toUShort())
        UnlockCommand.addCounter(builder, counter.toUShort())
        UnlockCommand.addSerialNumber(builder, serialNumber.toUShort())
        val unlockOffset = UnlockCommand.endUnlockCommand(builder)

        builder.finish(unlockOffset)
        val signature = signFlatBufferTable(builder.sizedByteArray(), privateKey)

        val signatureOffset = builder.createByteVector(signature)
        val botAuthorityIdentifier = builder.createString(botName)

        SignedMessage.startSignedMessage(builder)
        SignedMessage.addSignature(builder, signatureOffset)
        SignedMessage.addPayload(builder, unlockOffset)
        SignedMessage.addPayloadType(
            builder,
            club.subjugated.fb.message.MessagePayload.UnlockCommand,
        )
        SignedMessage.addAuthorityIdentifier(builder, botAuthorityIdentifier)
        val signedMessageOffset = SignedMessage.endSignedMessage(builder)

        builder.finish(signedMessageOffset)
        return builder.sizedByteArray()
    }

    fun makeLockCommand(
        botName: String,
        contractName: String,
        contractSerialNumber: Int,
        counter: Int,
        privateKey: ByteArray,
    ): ByteArray {
        val builder = FlatBufferBuilder(1024)

        val serialNumber = Random.nextInt(0, 1 shl 16)
        LockCommand.startLockCommand(builder)
        LockCommand.addContractSerialNumber(builder, contractSerialNumber.toUShort())
        LockCommand.addCounter(builder, counter.toUShort())
        LockCommand.addSerialNumber(builder, serialNumber.toUShort())
        val lockOffset = LockCommand.endLockCommand(builder)

        builder.finish(lockOffset)
        val signature = signFlatBufferTable(builder.sizedByteArray(), privateKey)

        val signatureOffset = builder.createByteVector(signature)
        val botAuthorityIdentifier = builder.createString(botName)

        SignedMessage.startSignedMessage(builder)
        SignedMessage.addSignature(builder, signatureOffset)
        SignedMessage.addPayload(builder, lockOffset)
        SignedMessage.addPayloadType(
            builder,
            club.subjugated.fb.message.MessagePayload.LockCommand,
        )
        SignedMessage.addAuthorityIdentifier(builder, botAuthorityIdentifier)
        val signedMessageOffset = SignedMessage.endSignedMessage(builder)

        builder.finish(signedMessageOffset)
        return builder.sizedByteArray()
    }

    fun buildSignedContractCommand(
        botName: String,
        terms: String,
        temporaryUnlock: Boolean,
        privateKey: ByteArray,
        publicKey: ByteArray,
        coBots: List<CoBotGrant> = emptyList(),
    ): ContractCommandWrapper {
        val builder = FlatBufferBuilder(1024)

        val coBotOffsets = coBots.map { grant ->
            Permission.startPermission(builder)
            Permission.addReceiveEvents(builder, grant.receiveEvents)
            Permission.addCanUnlock(builder, grant.canUnlock)
            Permission.addCanLock(builder, grant.canLock)
            Permission.addCanRelease(builder, grant.canRelease)
            val permOffset = Permission.endPermission(builder)

            val coBotNameOffset = builder.createString(grant.botName)
            val coBotPubOffset = builder.createByteVector(grant.compressedPublicKey)

            Bot.startBot(builder)
            Bot.addName(builder, coBotNameOffset)
            Bot.addPublicKey(builder, coBotPubOffset)
            Bot.addPermissions(builder, permOffset)
            Bot.endBot(builder)
        }

        // Primary issuing bot permissions
        Permission.startPermission(builder)
        Permission.addReceiveEvents(builder, true)
        Permission.addCanUnlock(builder, true)
        Permission.addCanLock(builder, true)
        Permission.addCanRelease(builder, true)
        val primaryPermissionOffset = Permission.endPermission(builder)

        val primaryBotNameOffset = builder.createString(botName)
        val publicKeyOffset = builder.createByteVector(publicKey)

        Bot.startBot(builder)
        Bot.addName(builder, primaryBotNameOffset)
        Bot.addPublicKey(builder, publicKeyOffset)
        Bot.addPermissions(builder, primaryPermissionOffset)
        val primaryBotOffset = Bot.endBot(builder)

        val termsOffset = builder.createString(terms)
        val allBotOffsets = listOf(primaryBotOffset) + coBotOffsets

        club.subjugated.fb.message.Contract.startBotsVector(builder, allBotOffsets.size)
        for (i in allBotOffsets.indices.reversed()) {
            builder.addOffset(allBotOffsets[i])
        }
        val botsVectorOffset = builder.endVector()

        val serialNumber = Random.nextInt(0, 1 shl 16)

        club.subjugated.fb.message.Contract.startContract(builder)
        club.subjugated.fb.message.Contract.addSerialNumber(builder, serialNumber.toUShort())
        club.subjugated.fb.message.Contract.addPublicKey(builder, publicKeyOffset)
        club.subjugated.fb.message.Contract.addTerms(builder, termsOffset)
        club.subjugated.fb.message.Contract.addIsTemporaryUnlockAllowed(builder, temporaryUnlock)
        club.subjugated.fb.message.Contract.addBots(builder, botsVectorOffset)
        val contractOffset = club.subjugated.fb.message.Contract.endContract(builder)

        builder.finish(contractOffset)
        val signature = signFlatBufferTable(builder.sizedByteArray(), privateKey)
        val signatureOffset = builder.createByteVector(signature)

        SignedMessage.startSignedMessage(builder)
        SignedMessage.addSignature(builder, signatureOffset)
        SignedMessage.addPayload(builder, contractOffset)
        SignedMessage.addPayloadType(
            builder,
            club.subjugated.fb.message.MessagePayload.Contract,
        )
        // NB: Even if a bot is the issuer of the contract we don't set the authority identifier
        // because that will break contract acceptance on the lock.
        val signedMessageOffset = SignedMessage.endSignedMessage(builder)

        builder.finish(signedMessageOffset)
        val signedMessageBytes = builder.sizedByteArray()

        return ContractCommandWrapper(signedMessageBytes, serialNumber)
    }

    fun makeCreateContractCommand(
        botName: String,
        shareableToken: String,
        terms: String,
        temporaryUnlock: Boolean,
        privateKey: ByteArray,
        publicKey: ByteArray,
        public: Boolean,
    ): ContractCommandWrapper {
        val coBots = if (public) {
            val announcerBotMap = botMapService.getBotMap("announcer")
            val compressedPublicKeyBytes = encodePublicKeySecp1(
                loadECPublicKeyFromPkcs8(announcerBotMap.publicKey!!) as ECPublicKey
            )
            listOf(
                CoBotGrant(
                    botName = announcerBotMap.externalName,
                    compressedPublicKey = compressedPublicKeyBytes,
                    receiveEvents = true,
                    canUnlock = false,
                    canLock = false,
                    canRelease = false,
                )
            )
        } else {
            emptyList()
        }

        return buildSignedContractCommand(
            botName = botName,
            terms = terms,
            temporaryUnlock = temporaryUnlock,
            privateKey = privateKey,
            publicKey = publicKey,
            coBots = coBots,
        )
    }

    fun createContract(botName: String, lockSessionToken: String, serialNumber: Int): Contract {
        val contract = Contract(
            botName = botName,
            lockSessionToken = lockSessionToken,
            serialNumber = serialNumber,
            createdAt = timeSource.nowInUtc(),
            updatedAt = timeSource.nowInUtc(),
            state = ContractState.UNSPECIFIED,
        )
        contractRepository.save(contract)
        return contract
    }
}
