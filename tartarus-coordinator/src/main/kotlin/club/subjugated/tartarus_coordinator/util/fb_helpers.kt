package club.subjugated.tartarus_coordinator.util

import club.subjugated.fb.message.*
import com.google.flatbuffers.Table
import java.lang.reflect.Field
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.Signature

sealed class ValidatedPayload {
    data class ContractPayload(val contract: Contract) : ValidatedPayload()
//    data class SimpleContractPayload(val contract: SimpleContract) : ValidatedPayload()
    data class LockUpdateEventPayload(val lockUpdateEvent: LockUpdateEvent) : ValidatedPayload()
    object UnknownPayload : ValidatedPayload()
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

    if(signedMessage.payloadType == MessagePayload.LockUpdateEvent) {
        val update = LockUpdateEvent()
        signedMessage.payload(update)
        val key = ByteArray(update.publicKeyLength) { update.publicKey(it).toByte() }
        if(verifySignedMessageSignature(update, key, signatureBytes)) {
            return ValidatedPayload.LockUpdateEventPayload(update)
        }
    }

    return ValidatedPayload.UnknownPayload
}

fun <T : Table> verifySignedMessageSignature(table : T, key : ByteArray, signature : ByteArray) : Boolean {
//    println("key ${key.joinToString(" ")}")

    val pubKey = getECPublicKeyFromByteArray(key)
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

