package club.subjugated.overlord_exe.services

import club.subjugated.fb.bots.BotApiMessage
import club.subjugated.fb.bots.CreateCommandRequest
import club.subjugated.fb.bots.CreateContractRequest
import club.subjugated.fb.bots.CreateMessageRequest
import club.subjugated.fb.bots.GetContractRequest
import club.subjugated.fb.bots.MessagePayload
import club.subjugated.fb.message.Bot
import club.subjugated.fb.message.Permission
import club.subjugated.fb.message.ReleaseCommand
import club.subjugated.fb.message.SignedMessage
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractState
import club.subjugated.overlord_exe.storage.ContractRepository
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.derToRawSignature
import com.google.flatbuffers.FlatBufferBuilder
import org.springframework.stereotype.Service
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.random.Random

@Service
class ContractService(
    private var contractRepository: ContractRepository,
    private var timeSource: TimeSource
) {

    fun getLiveContractsForBot(botName : String) : List<Contract> {
        return contractRepository.findByBotNameAndStateIn(botName, listOf(ContractState.ACCEPTED, ContractState.CONFIRMED))
    }

    fun getOrCreateContract(botName : String, lockSessionToken: String, serialNumber : Int) : Contract {
        return contractRepository.findByBotNameAndSerialNumber(botName, serialNumber) ?: run {
            createContract(botName, lockSessionToken, serialNumber)
        }
    }

    fun getContract(botName: String, serialNumber: Int) : Contract {
        return contractRepository.findByBotNameAndSerialNumber(botName, serialNumber)!!
    }

    fun markReleased(botName: String, serialNumber: Int) : Contract {
        val contract = getContract(botName, serialNumber)
        contract.state = ContractState.RELEASED
        contract.updatedAt = timeSource.nowInUtc()
        contractRepository.save(contract)
        return contract
    }

    fun save(contract: Contract) : Contract {
        return contractRepository.save(contract)
    }

    fun makeContractRequest(botName : String, lockSession : String, contractSerialNumber : UShort) : ByteArray {
        val builder = FlatBufferBuilder(1024)

        val lockSessionOffset = builder.createString(lockSession)

        GetContractRequest.startGetContractRequest(builder)
        GetContractRequest.addLockSession(builder, lockSessionOffset)
        GetContractRequest.addContractSerialNumber(builder, contractSerialNumber)
        val contractRequestOffset = GetContractRequest.endGetContractRequest(builder)

        val botNameOffset = builder.createString(botName)

        BotApiMessage.startBotApiMessage(builder)
        BotApiMessage.addName(builder, botNameOffset)
        BotApiMessage.addPayload(builder, contractRequestOffset)
        BotApiMessage.addPayloadType(builder, MessagePayload.GetContractRequest)
        BotApiMessage.addRequestId(builder, Random.nextLong())
        val botApiMessageOffset = BotApiMessage.endBotApiMessage(builder)

        builder.finish(botApiMessageOffset)

        return builder.sizedByteArray()
    }

    fun makeReleaseCommand(botName : String, contractName : String, contractSerialNumber: Int, privateKey : ByteArray) : ByteArray {
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
        val privateKey = keyFactory.generatePrivate(keySpec)

        var signature = Signature.getInstance("SHA256withECDSA", "BC").apply {
            initSign(privateKey)
            update(hash)
        }.sign()
        signature = derToRawSignature(signature)

        val signatureOffset = builder.createByteVector(signature)
        val botAuthorityIdentifer = builder.createString(botName)

        SignedMessage.startSignedMessage(builder)
        SignedMessage.addSignature(builder, signatureOffset)
        SignedMessage.addPayload(builder, releaseOffset)
        SignedMessage.addPayloadType(builder, club.subjugated.fb.message.MessagePayload.ReleaseCommand)
        SignedMessage.addAuthorityIdentifier(builder, botAuthorityIdentifer)
        val signedMessageOffset = SignedMessage.endSignedMessage(builder)

        builder.finish(signedMessageOffset)
        val signedMessageBytes = builder.sizedByteArray()

        // --- Build CreateCommandRequest ---
        val builder3 = FlatBufferBuilder(1024)
        val commandOffset = builder3.createByteVector(signedMessageBytes)
        val contractNameOffset = builder3.createString(contractName)
//        val shareableTokenOffset = builder3.createString(shareableToken)

        CreateCommandRequest.startCreateCommandRequest(builder3)
        CreateCommandRequest.addCommandBody(builder3, commandOffset)
        CreateCommandRequest.addContractName(builder3, contractNameOffset)
//        CreateCommandRequest.addShareableToken(builder3, shareableTokenOffset)
        val createRequestOffset = CreateCommandRequest.endCreateCommandRequest(builder3)

        // --- Build BotApiMessage ---
        val botNameOffset = builder3.createString(botName)
        val requestId = Random.nextLong(Long.MAX_VALUE)

        BotApiMessage.startBotApiMessage(builder3)
        BotApiMessage.addName(builder3, botNameOffset)
        BotApiMessage.addPayloadType(builder3, MessagePayload.CreateCommandRequest)
        BotApiMessage.addPayload(builder3, createRequestOffset)
        BotApiMessage.addRequestId(builder3, requestId)
        val botApiMessageOffset = BotApiMessage.endBotApiMessage(builder3)

        builder3.finish(botApiMessageOffset)
        return builder3.sizedByteArray()
    }

    fun makeCreateContractCommand(botName : String, shareableToken: String, terms: String, temporaryUnlock: Boolean, privateKey: ByteArray, publicKey : ByteArray) : ByteArray {
        val builder = FlatBufferBuilder(1024)

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
        club.subjugated.fb.message.Contract.startBotsVector(builder, 1)
        builder.addOffset(botOffset)
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
        val contractBytes = builder.sizedByteArray()

        // Hash payload
        val start = contractBytes[0].toInt() and 0xFF
        val contractStart = contractBytes[start].toInt() and 0xFF
        val vtableStart = start - contractStart
        val payloadToHash = contractBytes.copyOfRange(vtableStart, contractBytes.size)
        val hash = MessageDigest.getInstance("SHA-256").digest(payloadToHash)

        val keySpec = PKCS8EncodedKeySpec(privateKey)
        val keyFactory = KeyFactory.getInstance("EC", "BC")
        val privateKey = keyFactory.generatePrivate(keySpec)

        var signature = Signature.getInstance("SHA256withECDSA", "BC").apply {
            initSign(privateKey)
            update(hash)
        }.sign()
        signature = derToRawSignature(signature)

        val signatureOffset = builder.createByteVector(signature)

        SignedMessage.startSignedMessage(builder)
        SignedMessage.addSignature(builder, signatureOffset)
        SignedMessage.addPayload(builder, contractOffset)
        SignedMessage.addPayloadType(builder, club.subjugated.fb.message.MessagePayload.Contract)
        // NB: Even if a bot is the issuer of the contract we don't set the authority identifier
        // because that will break contract acceptance. The lock will try and validate against a key
        // it doesn't have.
        // SignedMessage.addAuthorityIdentifier(builder, botAuthorityIdentifer)
        val signedMessageOffset = SignedMessage.endSignedMessage(builder)

        builder.finish(signedMessageOffset)
        val signedMessageBytes = builder.sizedByteArray()

        // --- Build CreateCommandRequest ---
        val builder3 = FlatBufferBuilder(1024)
        val contractAsSignedMessageOffset = builder3.createByteVector(signedMessageBytes)

        val shareableTokenOffset = builder3.createString(shareableToken)

        CreateContractRequest.startCreateContractRequest(builder3)
        CreateContractRequest.addShareableToken(builder3, shareableTokenOffset)
        CreateContractRequest.addContract(builder3, contractAsSignedMessageOffset)
        val createContractOffset = CreateContractRequest.endCreateContractRequest(builder3)

        // --- Build BotApiMessage ---
        val botNameOffset = builder3.createString(botName)
        val requestId = Random.nextLong(Long.MAX_VALUE)

        BotApiMessage.startBotApiMessage(builder3)
        BotApiMessage.addName(builder3, botNameOffset)
        BotApiMessage.addPayloadType(builder3, MessagePayload.CreateContractRequest)
        BotApiMessage.addPayload(builder3, createContractOffset)
        BotApiMessage.addRequestId(builder3, requestId)
        val botApiMessageOffset = BotApiMessage.endBotApiMessage(builder3)

        builder3.finish(botApiMessageOffset)
        return builder3.sizedByteArray()
    }

    fun makeAddMessageToContractMessage(botName: String, contractName: String, message: String) : ByteArray {
        val builder3 = FlatBufferBuilder(1024)

        val contractNameOffset = builder3.createString(contractName)
        val messageOffset = builder3.createString(message)

        CreateMessageRequest.startCreateMessageRequest(builder3)
        CreateMessageRequest.addContractName(builder3, contractNameOffset)
        CreateMessageRequest.addMessage(builder3, messageOffset)
        val createRequestOffset = CreateMessageRequest.endCreateMessageRequest(builder3)

        // --- Build BotApiMessage ---
        val botNameOffset = builder3.createString(botName)
        val requestId = Random.nextLong(Long.MAX_VALUE)

        BotApiMessage.startBotApiMessage(builder3)
        BotApiMessage.addName(builder3, botNameOffset)
        BotApiMessage.addPayloadType(builder3, MessagePayload.CreateMessageRequest)
        BotApiMessage.addPayload(builder3, createRequestOffset)
        BotApiMessage.addRequestId(builder3, requestId)
        val botApiMessageOffset = BotApiMessage.endBotApiMessage(builder3)

        builder3.finish(botApiMessageOffset)
        return builder3.sizedByteArray()
    }

    fun createContract(botName : String, lockSessionToken: String, serialNumber: Int) : Contract {
        val contract = Contract(
            botName = botName,
            lockSessionToken = lockSessionToken,
            serialNumber = serialNumber,
            createdAt = timeSource.nowInUtc(),
            updatedAt = timeSource.nowInUtc(),
            state = ContractState.UNSPECIFIED
        )
        contractRepository.save(contract)
        return contract
    }
}