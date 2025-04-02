package club.subjugated.tartarus_coordinator.util

import club.subjugated.fb.message.Contract
import club.subjugated.fb.message.MessagePayload
import club.subjugated.fb.message.SignedMessage
import com.google.flatbuffers.Table
import java.lang.reflect.Field
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.Signature

sealed class ValidatedPayload {
    data class ContractPayload(val contract: Contract) : ValidatedPayload()
    object UnknownPayload : ValidatedPayload()
}

fun  signedMessageBytesValidator(buf : ByteBuffer) : ValidatedPayload {
    val signedMessage = SignedMessage.getRootAsSignedMessage(buf)

    val signatureBytes = ByteArray(signedMessage.signatureLength) { signedMessage.signature(it).toByte() }

    if(signedMessage.payloadType == MessagePayload.Contract) {
        val contract = Contract()
        signedMessage.payload(contract)

        val key = ByteArray(contract.publicKeyLength) { contract.publicKey(it).toByte() }
        println("key ${key.joinToString(" ")}")

        val pubKey = getECPublicKeyFromByteArray(key)
        val justContractBytes = getBytesOfTableWithVTable(contract)

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(justContractBytes)
        val hash = digest.digest()

        val verifier = Signature.getInstance("SHA256withECDSA", "BC").apply {
            initVerify(pubKey)
            update(hash)
        }

        val derEncodedSignature = rawToDerSignature(signatureBytes)
        if (verifier.verify(derEncodedSignature)) {
            return ValidatedPayload.ContractPayload(contract)
        }

    }

    return ValidatedPayload.UnknownPayload
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

