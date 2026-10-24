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
import club.subjugated.overlord_exe.storage.ContractRepository
import club.subjugated.overlord_exe.util.ContractCommandWrapper
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.derToRawSignature
import club.subjugated.overlord_exe.util.encodePublicKeySecp1
import club.subjugated.overlord_exe.util.loadECPublicKeyFromPkcs8
import com.google.flatbuffers.FlatBufferBuilder
import kotlinx.serialization.Serializable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
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

    fun getLiveContractsForBot(botName: String): List<Contract> {
        return contractRepository.findByBotNameAndStateIn(
            botName,
            listOf(ContractState.ACCEPTED, ContractState.CONFIRMED),
        )
    }

    fun getOrCreateContract(
        botName: String,
        lockSessionToken: String,
        serialNumber: Int,
    ): Contract {
        return contractRepository.findByBotNameAndSerialNumber(botName, serialNumber)
            ?: run { createContract(botName, lockSessionToken, serialNumber) }
    }

    fun getContract(botName: String, serialNumber: Int): Contract {
        return contractRepository.findByBotNameAndSerialNumber(botName, serialNumber)!!
    }

    fun markReleased(botName: String, serialNumber: Int): Contract {
        val contract = getContract(botName, serialNumber)
        contract.state = ContractState.RELEASED
        contract.updatedAt = timeSource.nowInUtc()
        contractRepository.save(contract)
        return contract
    }

    fun save(contract: Contract): Contract {
        return contractRepository.save(contract)
    }

    fun updateContractWithGetContractResponse(
        contract: Contract,
        response: BotContractInfo,
    ): Contract {
        contract.signedMessage = response.body?.let { Base64.getDecoder().decode(it) }
        contract.state = ContractState.valueOf(response.state!!)
        contract.externalContractName = response.name
        contract.shareableToken = response.shareableToken
        contract.nextCounter = response.nextCounter
        contractRepository.save(contract)
        return contract
    }

    @EventListener
    fun handleIssueContractRequest(event: IssueContract) {
        val compressedPublicKey =
            encodePublicKeySecp1(
                loadECPublicKeyFromPkcs8(event.botMap.publicKey!!) as ECPublicKey
            )
        val wrapper =
            makeCreateContractCommand(
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
                action =
                    BotRestAction.CreateContract(
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
                action =
                    BotRestAction.CreateMessage(
                        contractName = event.contract.externalContractName!!,
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

        val releaseCommandBytes =
            makeReleaseCommand(
                event.botMap.externalName,
                event.contract.externalContractName!!,
                event.contract.serialNumber,
                event.botMap.privateKey!!,
            )
        val signedMessageBase64 = Base64.getEncoder().encodeToString(releaseCommandBytes)
        applicationEventPublisher.publishEvent(
            SendBotBytes(
                source = this,
                botMap = event.botMap,
                action =
                    BotRestAction.CreateCommand(
                        contractName = event.contract.externalContractName!!,
                        shareableToken = event.contract.shareableToken,
                        signedMessageBase64 = signedMessageBase64,
                    ),
            )
        )
    }

    @EventListener
    fun handleIssueUnlock(event: IssueUnlock) {
        if (environment.activeProfiles.contains("test")) return

        val unlockCommandBytes =
            makeUnlockCommand(
                event.botMap.externalName,
                event.contract.externalContractName!!,
                event.contract.serialNumber,
                event.contract.nextCounter,
                event.botMap.privateKey!!,
            )
        val signedMessageBase64 = Base64.getEncoder().encodeToString(unlockCommandBytes)
        applicationEventPublisher.publishEvent(
            SendBotBytes(
                source = this,
                botMap = event.botMap,
                action =
                    BotRestAction.CreateCommand(
                        contractName = event.contract.externalContractName!!,
                        shareableToken = event.contract.shareableToken,
                        signedMessageBase64 = signedMessageBase64,
                    ),
            )
        )
    }

    @EventListener
    fun handleIssueLock(event: IssueLock) {
        if (environment.activeProfiles.contains("test")) return

        val lockCommandBytes =
            makeLockCommand(
                event.botMap.externalName,
                event.contract.externalContractName!!,
                event.contract.serialNumber,
                event.contract.nextCounter,
                event.botMap.privateKey!!,
            )
        val signedMessageBase64 = Base64.getEncoder().encodeToString(lockCommandBytes)
        applicationEventPublisher.publishEvent(
            SendBotBytes(
                source = this,
                botMap = event.botMap,
                action =
                    BotRestAction.CreateCommand(
                        contractName = event.contract.externalContractName!!,
                        shareableToken = event.contract.shareableToken,
                        signedMessageBase64 = signedMessageBase64,
                    ),
            )
        )
    }

    fun makeReleaseCommand(
        botName: String,
        contractName: String,
        contractSerialNumber: Int,
        privateKey: ByteArray,
    ): ByteArray {
        val builder = FlatBufferBuilder(1024)

        // --- Build ReleaseCommand ---
        val serialNumber = Random.nextInt(0, 1 shl 16)
        ReleaseCommand.startReleaseCommand(builder)
        ReleaseCommand.addContractSerialNumber(builder, contractSerialNumber.toUShort())
        ReleaseCommand.addCounter(builder, 2000.toUShort())
        ReleaseCommand.addSerialNumber(builder, serialNumber.toUShort())
        val releaseOffset = ReleaseCommand.endReleaseCommand(builder)

        builder.finish(releaseOffset)
        val releaseBytes = builder.sizedByteArray()

        // Hash payload
        val start = releaseBytes[0].toInt() and 0xFF
        val contractStart = releaseBytes[start].toInt() and 0xFF
        val vtableStart = start - contractStart
        val payloadToHash = releaseBytes.copyOfRange(vtableStart, releaseBytes.size)
        val hash = MessageDigest.getInstance("SHA-256").digest(payloadToHash)

        val keySpec = PKCS8EncodedKeySpec(privateKey)
        val keyFactory = KeyFactory.getInstance("EC", "BC")
        val ecPrivateKey = keyFactory.generatePrivate(keySpec)

        var signature =
            Signature.getInstance("SHA256withECDSA", "BC")
                .apply {
                    initSign(ecPrivateKey)
                    update(hash)
                }
                .sign()
        signature = derToRawSignature(signature)

        val signatureOffset = builder.createByteVector(signature)
        val botAuthorityIdentifer = builder.createString(botName)

        SignedMessage.startSignedMessage(builder)
        SignedMessage.addSignature(builder, signatureOffset)
        SignedMessage.addPayload(builder, releaseOffset)
        SignedMessage.addPayloadType(
            builder,
            club.subjugated.fb.message.MessagePayload.ReleaseCommand,
        )
        SignedMessage.addAuthorityIdentifier(builder, botAuthorityIdentifer)
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

        // --- Build UnlockCommand ---
        val serialNumber = Random.nextInt(0, 1 shl 16)
        UnlockCommand.startUnlockCommand(builder)
        UnlockCommand.addContractSerialNumber(builder, contractSerialNumber.toUShort())
        UnlockCommand.addCounter(builder, counter.toUShort())
        UnlockCommand.addSerialNumber(builder, serialNumber.toUShort())
        val unlockOffset = UnlockCommand.endUnlockCommand(builder)

        builder.finish(unlockOffset)
        val unlockBytes = builder.sizedByteArray()

        // Hash payload
        val start = unlockBytes[0].toInt() and 0xFF
        val contractStart = unlockBytes[start].toInt() and 0xFF
        val vtableStart = start - contractStart
        val payloadToHash = unlockBytes.copyOfRange(vtableStart, unlockBytes.size)
        val hash = MessageDigest.getInstance("SHA-256").digest(payloadToHash)

        val keySpec = PKCS8EncodedKeySpec(privateKey)
        val keyFactory = KeyFactory.getInstance("EC", "BC")
        val ecPrivateKey = keyFactory.generatePrivate(keySpec)

        var signature =
            Signature.getInstance("SHA256withECDSA", "BC")
                .apply {
                    initSign(ecPrivateKey)
                    update(hash)
                }
                .sign()
        signature = derToRawSignature(signature)

        val signatureOffset = builder.createByteVector(signature)
        val botAuthorityIdentifer = builder.createString(botName)

        SignedMessage.startSignedMessage(builder)
        SignedMessage.addSignature(builder, signatureOffset)
        SignedMessage.addPayload(builder, unlockOffset)
        SignedMessage.addPayloadType(
            builder,
            club.subjugated.fb.message.MessagePayload.UnlockCommand,
        )
        SignedMessage.addAuthorityIdentifier(builder, botAuthorityIdentifer)
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

        // --- Build LockCommand ---
        val serialNumber = Random.nextInt(0, 1 shl 16)
        LockCommand.startLockCommand(builder)
        LockCommand.addContractSerialNumber(builder, contractSerialNumber.toUShort())
        LockCommand.addCounter(builder, counter.toUShort())
        LockCommand.addSerialNumber(builder, serialNumber.toUShort())
        val lockOffset = LockCommand.endLockCommand(builder)

        builder.finish(lockOffset)
        val lockBytes = builder.sizedByteArray()

        // Hash payload
        val start = lockBytes[0].toInt() and 0xFF
        val contractStart = lockBytes[start].toInt() and 0xFF
        val vtableStart = start - contractStart
        val payloadToHash = lockBytes.copyOfRange(vtableStart, lockBytes.size)
        val hash = MessageDigest.getInstance("SHA-256").digest(payloadToHash)

        val keySpec = PKCS8EncodedKeySpec(privateKey)
        val keyFactory = KeyFactory.getInstance("EC", "BC")
        val ecPrivateKey = keyFactory.generatePrivate(keySpec)

        var signature =
            Signature.getInstance("SHA256withECDSA", "BC")
                .apply {
                    initSign(ecPrivateKey)
                    update(hash)
                }
                .sign()
        signature = derToRawSignature(signature)

        val signatureOffset = builder.createByteVector(signature)
        val botAuthorityIdentifer = builder.createString(botName)

        SignedMessage.startSignedMessage(builder)
        SignedMessage.addSignature(builder, signatureOffset)
        SignedMessage.addPayload(builder, lockOffset)
        SignedMessage.addPayloadType(
            builder,
            club.subjugated.fb.message.MessagePayload.LockCommand,
        )
        SignedMessage.addAuthorityIdentifier(builder, botAuthorityIdentifer)
        val signedMessageOffset = SignedMessage.endSignedMessage(builder)

        builder.finish(signedMessageOffset)
        return builder.sizedByteArray()
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
        val builder = FlatBufferBuilder(1024)

        var announcerBotOffset: Int? = 0

        if (public) {
            val announcerBotMap = botMapService.getBotMap("announcer")

            // Build permission for Announcer
            Permission.startPermission(builder)
            Permission.addReceiveEvents(builder, true)
            Permission.addCanUnlock(builder, false)
            Permission.addCanRelease(builder, false)
            val announcerPermissionOffset = Permission.endPermission(builder)

            val announcerBotName = builder.createString(announcerBotMap.externalName)
            val compressedPublicKeyBytes =
                encodePublicKeySecp1(
                    loadECPublicKeyFromPkcs8(announcerBotMap.publicKey!!) as ECPublicKey
                )
            val pubKeyOffset = builder.createByteVector(compressedPublicKeyBytes)

            Bot.startBot(builder)
            Bot.addName(builder, announcerBotName)
            Bot.addPublicKey(builder, pubKeyOffset)
            Bot.addPermissions(builder, announcerPermissionOffset)
            announcerBotOffset = Bot.endBot(builder)
        }

        // Build Permission
        Permission.startPermission(builder)
        Permission.addReceiveEvents(builder, true)
        Permission.addCanUnlock(builder, true)
        Permission.addCanRelease(builder, true)
        val permissionOffset = Permission.endPermission(builder)

        val botNameOffset1 = builder.createString(botName)
        val publicKeyOffset = builder.createByteVector(publicKey)

        Bot.startBot(builder)
        Bot.addName(builder, botNameOffset1)
        Bot.addPublicKey(builder, publicKeyOffset)
        Bot.addPermissions(builder, permissionOffset)
        val botOffset = Bot.endBot(builder)

        val termsOffset = builder.createString(terms)
        val botsVectorOffset =
            if (public) {
                club.subjugated.fb.message.Contract.startBotsVector(builder, 2)
                builder.addOffset(botOffset)
                builder.addOffset(announcerBotOffset!!)
                builder.endVector()
            } else {
                club.subjugated.fb.message.Contract.startBotsVector(builder, 1)
                builder.addOffset(botOffset)
                builder.endVector()
            }

        val serialNumber = Random.nextInt(0, 1 shl 16)

        club.subjugated.fb.message.Contract.startContract(builder)
        club.subjugated.fb.message.Contract.addSerialNumber(builder, serialNumber.toUShort())
        club.subjugated.fb.message.Contract.addPublicKey(builder, publicKeyOffset)
        club.subjugated.fb.message.Contract.addTerms(builder, termsOffset)
        club.subjugated.fb.message.Contract.addIsTemporaryUnlockAllowed(builder, temporaryUnlock)
        club.subjugated.fb.message.Contract.addBots(builder, botsVectorOffset)
        val contractOffset = club.subjugated.fb.message.Contract.endContract(builder)

        builder.finish(contractOffset)
        val contractBytes = builder.sizedByteArray()

        // Hash payload
        val start = contractBytes[0].toInt() and 0xFF
        val contractStart = contractBytes[start].toInt() and 0xFF
        val vtableStart = start - contractStart
        val payloadToHash = contractBytes.copyOfRange(vtableStart, contractBytes.size)
        val hash = MessageDigest.getInstance("SHA-256").digest(payloadToHash)

        val keySpec = PKCS8EncodedKeySpec(privateKey)
        val keyFactory = KeyFactory.getInstance("EC", "BC")
        val ecPrivateKey = keyFactory.generatePrivate(keySpec)

        var signature =
            Signature.getInstance("SHA256withECDSA", "BC")
                .apply {
                    initSign(ecPrivateKey)
                    update(hash)
                }
                .sign()
        signature = derToRawSignature(signature)

        val signatureOffset = builder.createByteVector(signature)

        SignedMessage.startSignedMessage(builder)
        SignedMessage.addSignature(builder, signatureOffset)
        SignedMessage.addPayload(builder, contractOffset)
        SignedMessage.addPayloadType(
            builder,
            club.subjugated.fb.message.MessagePayload.Contract,
        )
        // NB: Even if a bot is the issuer of the contract we don't set the authority identifier
        // because that will break contract acceptance. The lock will try and validate against a key
        // it doesn't have.
        // SignedMessage.addAuthorityIdentifier(builder, botAuthorityIdentifer)
        val signedMessageOffset = SignedMessage.endSignedMessage(builder)

        builder.finish(signedMessageOffset)
        val signedMessageBytes = builder.sizedByteArray()

        return ContractCommandWrapper(signedMessageBytes, serialNumber)
    }

    fun createContract(botName: String, lockSessionToken: String, serialNumber: Int): Contract {
        val contract =
            Contract(
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
