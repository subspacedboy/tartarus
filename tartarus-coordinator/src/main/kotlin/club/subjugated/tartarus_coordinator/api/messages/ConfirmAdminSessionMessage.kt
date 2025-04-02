package club.subjugated.tartarus_coordinator.api.messages

import club.subjugated.tartarus_coordinator.util.getECPublicKeyFromCompressedKeyByteArray
import club.subjugated.tartarus_coordinator.util.rawToDerSignature
import java.security.MessageDigest
import java.security.Signature
import java.util.*

data class ConfirmAdminSessionMessage(
    val publicKey: String? = null,
    val sessionToken: String? = null,
    val signature: String? = null,
) {
    fun validateOrThrow() {
        val decodedKeyBytes = Base64.getDecoder().decode(publicKey)

        val key = getECPublicKeyFromCompressedKeyByteArray(decodedKeyBytes)
        val signatureBytes = Base64.getDecoder().decode(signature)
        val derSignature = rawToDerSignature(signatureBytes)

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(sessionToken!!.encodeToByteArray())
        val hash = digest.digest()

        val verifier =
            Signature.getInstance("SHA256withECDSA", "BC").apply {
                initVerify(key)
                update(hash)
            }

        if (!verifier.verify(derSignature)) {
            throw IllegalArgumentException("Invalid signature on session token")
        }
    }
}