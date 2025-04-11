package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.api.messages.LockUserSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.NewLockUserSessionMessage
import club.subjugated.tartarus_coordinator.services.LockSessionService
import club.subjugated.tartarus_coordinator.services.LockUserSessionService
import club.subjugated.tartarus_coordinator.util.getECPublicKeyFromCompressedKeyByteArray
import jakarta.ws.rs.core.MediaType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.KeyAgreement
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.asn1.sec.ECPrivateKey
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECPrivateKeySpec
import org.bouncycastle.jce.ECNamedCurveTable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import sun.net.www.content.text.plain
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@RestController
@RequestMapping("/lock_user_sessions")
@Controller
class LockUserSessionController(
    private var lockUserSessionService: LockUserSessionService,
    private var lockSessionService: LockSessionService,

) {
    @PostMapping("/", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun createLockUserSession(@RequestBody newLockUserSessionMessage: NewLockUserSessionMessage) : ResponseEntity<LockUserSessionMessage> {
        val lockSession = lockSessionService.findBySessionToken(newLockUserSessionMessage.sessionToken!!)
        val lockUserSession = lockUserSessionService.authenticateViaCryptogram(
            lockSession!!,
            newLockUserSessionMessage.cipher!!,
            newLockUserSessionMessage.nonce!!,
            newLockUserSessionMessage.sessionToken!!,
            newLockUserSessionMessage.lockUserSessionPublicKey!!)

        if(lockUserSession == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return ResponseEntity.ok(LockUserSessionMessage.fromLockUserSession(lockUserSession))
    }
}