package club.subjugated.tartarus_coordinator.util

import club.subjugated.fb.message.*
import com.google.flatbuffers.Table
import java.lang.reflect.Field
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.Signature

sealed class ValidatedPayload {
    data class ContractPayload(val contract: Contract) : ValidatedPayload()
    data class UnlockCommandPayload(val unlockCommand: UnlockCommand) : ValidatedPayload()
    data class LockCommandPayload(val lockCommand: LockCommand) : ValidatedPayload()
    data class ReleaseCommandPayload(val releaseCommand: ReleaseCommand) : ValidatedPayload()
    data class StartedUpdatePayload(val startedUpdate: StartedUpdate) : ValidatedPayload()
    data class AcknowledgementPayload(val acknowledgement: Acknowledgement) : ValidatedPayload()
    data class ErrorPayload(val error: Error) : ValidatedPayload()
    object UnknownPayload : ValidatedPayload()
}

sealed class ValidationKeyRequirement {

    data object KeyIsInMessage : ValidationKeyRequirement()
    data object AuthorSessionKey : ValidationKeyRequirement()
    class LockSessionKey(var sessionToken : String) : ValidationKeyRequirement()
    data object Unspecified : ValidationKeyRequirement()
}


fun findVerificationKeyRequirement(buf : ByteBuffer) : ValidationKeyRequirement {
    val signedMessage = SignedMessage.getRootAsSignedMessage(buf)
    return when (signedMessage.payloadType) {
        MessagePayload.Contract -> ValidationKeyRequirement.KeyIsInMessage
        MessagePayload.Error -> {
            val error = Error()
            signedMessage.payload(error)
            ValidationKeyRequirement.LockSessionKey(error.session!!)
        }
        MessagePayload.Acknowledgement -> {
            val ack = Acknowledgement()
            signedMessage.payload(ack)
            ValidationKeyRequirement.LockSessionKey(ack.session!!)
        }
        MessagePayload.StartedUpdate -> ValidationKeyRequirement.KeyIsInMessage
        else -> ValidationKeyRequirement.Unspecified
    }
}

fun  signedMessageBytesValidator(buf : ByteBuffer) : ValidatedPayload {
    val signedMessage = SignedMessage.getRootAsSignedMessage(buf)
    val signatureBytes = ByteArray(signedMessage.signatureLength) { signedMessage.signature(it).toByte() }

    if(signedMessage.payloadType == MessagePayload.Contract) {
        val contract = Contract()
        signedMessage.payload(contract)
        val key = ByteArray(contract.publicKeyLength) { contract.publicKey(it).toByte() }
        if(verifySignedMessageSignature(contract, key, signatureBytes)) {
            return ValidatedPayload.ContractPayload(contract)
        }
    }

    if(signedMessage.payloadType == MessagePayload.StartedUpdate) {
        val update = StartedUpdate()
        signedMessage.payload(update)
        val key = ByteArray(update.publicKeyLength) { update.publicKey(it).toByte() }
        if(verifySignedMessageSignature(update, key, signatureBytes)) {
            return ValidatedPayload.StartedUpdatePayload(update)
        }
    }

    if(signedMessage.payloadType == MessagePayload.Acknowledgement) {
        val ack = Acknowledgement()
        signedMessage.payload(ack)
        val key = ByteArray(ack.publicKeyLength) { ack.publicKey(it).toByte() }
        if(verifySignedMessageSignature(ack, key, signatureBytes)) {
            return ValidatedPayload.AcknowledgementPayload(ack)
        }
    }

    return ValidatedPayload.UnknownPayload
}

/**
 * We need to validate a message, but the corresponding key isn't part of the SignedMessage.
 */
fun  signedMessageBytesValidatorWithExternalKey(buf : ByteBuffer, key: ByteArray) : ValidatedPayload {
    val signedMessage = SignedMessage.getRootAsSignedMessage(buf)
    val signatureBytes = ByteArray(signedMessage.signatureLength) { signedMessage.signature(it).toByte() }

    return when(signedMessage.payloadType) {
        MessagePayload.UnlockCommand -> {
            val unlock = UnlockCommand()
            signedMessage.payload(unlock)
            if(verifySignedMessageSignature(unlock, key, signatureBytes)) {
                ValidatedPayload.UnlockCommandPayload(unlock)
            } else {
                ValidatedPayload.UnknownPayload
            }
        }
        MessagePayload.LockCommand -> {
            val lock = LockCommand()
            signedMessage.payload(lock)
            if(verifySignedMessageSignature(lock, key, signatureBytes)) {
                ValidatedPayload.LockCommandPayload(lock)
            } else {
                ValidatedPayload.UnknownPayload
            }
        }
        MessagePayload.ReleaseCommand -> {
            val release = ReleaseCommand()
            signedMessage.payload(release)
            if(verifySignedMessageSignature(release, key, signatureBytes)) {
                ValidatedPayload.ReleaseCommandPayload(release)
            } else {
                ValidatedPayload.UnknownPayload
            }
        }
        MessagePayload.Acknowledgement -> {
            val ack = Acknowledgement()
            signedMessage.payload(ack)
            if(verifySignedMessageSignature(ack, key, signatureBytes)) {
                ValidatedPayload.AcknowledgementPayload(ack)
            } else {
                ValidatedPayload.UnknownPayload
            }
        }
        MessagePayload.Error -> {
            val error = Error()
            signedMessage.payload(error)
            if(verifySignedMessageSignature(error, key, signatureBytes)) {
                ValidatedPayload.ErrorPayload(error)
            } else {
                ValidatedPayload.UnknownPayload
            }
        }
        else -> {
            ValidatedPayload.UnknownPayload
        }
    }
}

fun <T : Table> verifySignedMessageSignature(table : T, key : ByteArray, signature : ByteArray) : Boolean {
//    println("key ${key.joinToString(" ")}")

    val pubKey = getECPublicKeyFromCompressedKeyByteArray(key)
    val justContractBytes = getBytesOfTableWithVTable(table)

    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(justContractBytes)
    val hash = digest.digest()

    val verifier = Signature.getInstance("SHA256withECDSA", "BC").apply {
        initVerify(pubKey)
        update(hash)
    }

    val derEncodedSignature = rawToDerSignature(signature)
    return verifier.verify(derEncodedSignature)
}


fun <T : Table> getBytesOfTableWithVTable(table : T) : ByteArray {
    val buffer = table.byteBuffer.duplicate()

    val vtableStart = getVTableStart(table)
    buffer.position(vtableStart)
    val remaining = buffer.remaining()
    val vtableAndMainTableBytes = ByteArray(remaining).apply { buffer.get(this) }
    return vtableAndMainTableBytes
}

fun <T : Table> getBbPos(table: T): Int {
    val field: Field = Table::class.java.getDeclaredField("bb_pos")
    field.isAccessible = true
    return field.getInt(table)
}

fun <T : Table> getVTableStart(table: T): Int {
    val byteBuffer = table.byteBuffer
    val tableStart = getBbPos(table)  // Equivalent to `bb_pos`
    val vtableOffset = byteBuffer.getShort(tableStart).toInt() // Offset to vtable
    return tableStart - vtableOffset
}

