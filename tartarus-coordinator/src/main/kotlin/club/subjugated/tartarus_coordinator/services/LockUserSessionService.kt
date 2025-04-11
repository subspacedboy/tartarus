package club.subjugated.tartarus_coordinator.services

import club.subjugated.tartarus_coordinator.api.messages.NewLockUserSessionMessage
import club.subjugated.tartarus_coordinator.models.LockSession
import club.subjugated.tartarus_coordinator.models.LockUserSession
import club.subjugated.tartarus_coordinator.storage.LockUserSessionRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import club.subjugated.tartarus_coordinator.util.getECPublicKeyFromCompressedKeyByteArray
import org.bouncycastle.asn1.sec.ECPrivateKey
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.spec.ECPrivateKeySpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.KeyFactory
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class LockUserSessionService(
    private var lockUserSessionRepository: LockUserSessionRepository,
    private var timeSource: TimeSource,
    @Value("\${tartarus.login_token_private_key}") var tokenPrivateKey: String,
) {
    fun saveNewLockUserSession(lockSession: LockSession, nonce: String, userPublicKey : String) : LockUserSession {
        val maybeLockUserSession = lockUserSessionRepository.findByPublicKey(userPublicKey) ?: LockUserSession(
            publicKey = userPublicKey,
            lockSession = lockSession,
            nonceUsed = nonce,
            createdAt = timeSource.nowInUtc(),
            updatedAt = timeSource.nowInUtc()
        ).let { lockUserSessionRepository.save(it) }
        return maybeLockUserSession
    }

    fun findByPublicKey(userPublicKey : String) : LockUserSession {
        return lockUserSessionRepository.findByPublicKey(userPublicKey)!!
    }

    fun findByName(name : String) : LockUserSession {
        return lockUserSessionRepository.findByName(name)
    }

    fun getByNonceUsed(nonce : String) : LockUserSession? {
        val suspectSession = lockUserSessionRepository.findByNonceUsed(nonce)
        return suspectSession
    }

    fun authenticateViaCryptogram(lockSession : LockSession,
                                  cipherText : String,
                                  nonce: String,
                                  sessionToken : String,
                                  userPublicKey: String) : LockUserSession? {
        val keyBytes = Base64.getDecoder().decode(tokenPrivateKey)
        val ecKey = ECPrivateKey.getInstance(keyBytes) // Parses raw ECPrivateKey (not PKCS#8)

        val params = ECNamedCurveTable.getParameterSpec("secp256r1")
        val privSpec = ECPrivateKeySpec(ecKey.key, params)

        val keyFactory = KeyFactory.getInstance("EC", "BC")
        val privateKey = keyFactory.generatePrivate(privSpec)

        val publicKey = getECPublicKeyFromCompressedKeyByteArray(lockSession.decodePublicKey())

        val ka = KeyAgreement.getInstance("ECDH", "BC")
        ka.init(privateKey)
        ka.doPhase(publicKey, true)
        val sharedSecret = ka.generateSecret()

        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(sharedSecret, null, "aes-gcm key".toByteArray()))
        val derivedKey = ByteArray(32)
        hkdf.generateBytes(derivedKey, 0, 32)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC")

        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(derivedKey, "AES"),
            GCMParameterSpec(128, Base64.getUrlDecoder().decode(nonce))          // 128-bit tag, 12-byte nonce
        )
        val plain = cipher.doFinal(Base64.getUrlDecoder().decode(cipherText)).toString(Charsets.UTF_8)

        if(plain == sessionToken || plain == "LOGIN") {
            val preExistingSession = getByNonceUsed(nonce)
            // Either there shouldn't be a pre-existing session, or if there is, it should
            // have the same public key that we were just presented with.
            if(preExistingSession == null || preExistingSession.publicKey == userPublicKey) {
                return saveNewLockUserSession(lockSession, nonce, userPublicKey)
            }
        }

        return null
    }
}