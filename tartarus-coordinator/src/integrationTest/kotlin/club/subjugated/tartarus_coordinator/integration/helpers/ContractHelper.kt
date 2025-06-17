package club.subjugated.tartarus_coordinator.integration.helpers

import club.subjugated.fb.bots.BotApiMessage
import club.subjugated.fb.bots.CreateContractRequest
import club.subjugated.fb.bots.MessagePayload
import club.subjugated.fb.message.Bot
import club.subjugated.fb.message.Permission
import club.subjugated.fb.message.SignedMessage
import club.subjugated.tartarus_coordinator.util.derToRawSignature
import com.google.flatbuffers.FlatBufferBuilder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.random.Random

data class ContractCommandWrapper(
    var messageBytes : ByteArray,
    var contractSerialNumber: Int
)

fun makeCreateContractCommand(authorName : String, shareableToken: String, terms: String, temporaryUnlock: Boolean, privateKey: ByteArray, publicKey : ByteArray, public: Boolean) : ContractCommandWrapper {
    val builder = FlatBufferBuilder(1024)

    var announcerBotOffset : Int? = 0

    // Build Permission
    Permission.startPermission(builder)
    Permission.addReceiveEvents(builder, true)
    Permission.addCanUnlock(builder, true)
    Permission.addCanRelease(builder, true)
    val permissionOffset = Permission.endPermission(builder)

    val botNameOffset1 = builder.createString(authorName)
    val publicKeyOffset = builder.createByteVector(publicKey)

    Bot.startBot(builder)
    Bot.addName(builder, botNameOffset1)
    Bot.addPublicKey(builder, publicKeyOffset)
    Bot.addPermissions(builder, permissionOffset)
    val botOffset = Bot.endBot(builder)

    val termsOffset = builder.createString(terms)
    val botsVectorOffset = if(public) {
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

    return ContractCommandWrapper(signedMessageBytes, serialNumber)
}