package club.subjugated.tartarus_coordinator.utils

import club.subjugated.tartarus_coordinator.models.AuthorSession
import club.subjugated.tartarus_coordinator.models.LockSession
import club.subjugated.tartarus_coordinator.models.LockUserSession
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.LockUserSessionService
import club.subjugated.tartarus_coordinator.util.JwtValidationResult
import club.subjugated.tartarus_coordinator.util.encodePublicKeySecp1
import club.subjugated.tartarus_coordinator.util.generateECKeyPair
import club.subjugated.tartarus_coordinator.util.verifyJwt
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.Security
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class JwtHelpersTest {

    private fun createSignedJwt(subject: String?, privateKey: ECPrivateKey): String {
        val claimsBuilder = JWTClaimsSet.Builder()
        if (subject != null) {
            claimsBuilder.subject(subject)
        }
        val claims = claimsBuilder.build()
        val signedJWT = SignedJWT(JWSHeader(JWSAlgorithm.ES256), claims)
        signedJWT.sign(ECDSASigner(privateKey))
        return signedJWT.serialize()
    }

    @Test
    fun testVerifyJwt_ValidAuthorSession() {
        val keyPair = generateECKeyPair()
        val pubKeyBase64 =
            Base64.getEncoder().encodeToString(encodePublicKeySecp1(keyPair.public as ECPublicKey))
        val tokenName = "as-valid-author"

        val authorSession = AuthorSession(name = tokenName, publicKey = pubKeyBase64)
        val authorSessionService = mock(AuthorSessionService::class.java)
        val lockUserSessionService = mock(LockUserSessionService::class.java)
        `when`(authorSessionService.findByName(tokenName)).thenReturn(authorSession)

        val jwt = createSignedJwt(tokenName, keyPair.private as ECPrivateKey)
        val result = verifyJwt(authorSessionService, lockUserSessionService, jwt)

        assertThat(result).isInstanceOf(JwtValidationResult.AuthorSession::class.java)
        assertThat((result as JwtValidationResult.AuthorSession).value).isEqualTo(tokenName)
    }

    @Test
    fun testVerifyJwt_ValidLockUserSession() {
        val keyPair = generateECKeyPair()
        val pubKeyBase64 =
            Base64.getEncoder().encodeToString(encodePublicKeySecp1(keyPair.public as ECPublicKey))
        val tokenName = "u-valid-lockuser"

        val lockSession = mock(LockSession::class.java)
        val lockUserSession =
            LockUserSession(
                name = tokenName,
                publicKey = pubKeyBase64,
                nonceUsed = null,
                lockSession = lockSession,
            )
        val authorSessionService = mock(AuthorSessionService::class.java)
        val lockUserSessionService = mock(LockUserSessionService::class.java)
        `when`(lockUserSessionService.findByName(tokenName)).thenReturn(lockUserSession)

        val jwt = createSignedJwt(tokenName, keyPair.private as ECPrivateKey)
        val result = verifyJwt(authorSessionService, lockUserSessionService, jwt)

        assertThat(result).isInstanceOf(JwtValidationResult.LockUserSession::class.java)
        assertThat((result as JwtValidationResult.LockUserSession).value).isEqualTo(tokenName)
    }

    @Test
    fun testVerifyJwt_InvalidSignature() {
        val signingKeyPair = generateECKeyPair()
        val differentKeyPair = generateECKeyPair()
        val differentPubKeyBase64 =
            Base64.getEncoder()
                .encodeToString(encodePublicKeySecp1(differentKeyPair.public as ECPublicKey))
        val tokenName = "as-tampered"

        val authorSession = AuthorSession(name = tokenName, publicKey = differentPubKeyBase64)
        val authorSessionService = mock(AuthorSessionService::class.java)
        val lockUserSessionService = mock(LockUserSessionService::class.java)
        `when`(authorSessionService.findByName(tokenName)).thenReturn(authorSession)

        val jwt = createSignedJwt(tokenName, signingKeyPair.private as ECPrivateKey)
        val result = verifyJwt(authorSessionService, lockUserSessionService, jwt)

        assertThat(result).isEqualTo(JwtValidationResult.Invalid)
    }

    @Test
    fun testVerifyJwt_UnknownPrefix() {
        val keyPair = generateECKeyPair()
        val authorSessionService = mock(AuthorSessionService::class.java)
        val lockUserSessionService = mock(LockUserSessionService::class.java)

        val jwt = createSignedJwt("unknown-prefix-token", keyPair.private as ECPrivateKey)
        val result = verifyJwt(authorSessionService, lockUserSessionService, jwt)

        assertThat(result).isEqualTo(JwtValidationResult.Invalid)
    }

    @Test
    fun testVerifyJwt_MissingSubject() {
        val keyPair = generateECKeyPair()
        val authorSessionService = mock(AuthorSessionService::class.java)
        val lockUserSessionService = mock(LockUserSessionService::class.java)

        val jwt = createSignedJwt(null, keyPair.private as ECPrivateKey)
        val result = verifyJwt(authorSessionService, lockUserSessionService, jwt)

        assertThat(result).isEqualTo(JwtValidationResult.Invalid)
    }

    @Test
    fun testVerifyJwt_MalformedJwtString() {
        val authorSessionService = mock(AuthorSessionService::class.java)
        val lockUserSessionService = mock(LockUserSessionService::class.java)

        val result = verifyJwt(authorSessionService, lockUserSessionService, "not.a.valid.jwt")
        assertThat(result).isEqualTo(JwtValidationResult.Invalid)

        val resultEmpty = verifyJwt(authorSessionService, lockUserSessionService, "")
        assertThat(resultEmpty).isEqualTo(JwtValidationResult.Invalid)
    }

    @Test
    fun testVerifyJwt_SessionNotFoundThrowsException() {
        val keyPair = generateECKeyPair()
        val tokenName = "as-not-found"
        val authorSessionService = mock(AuthorSessionService::class.java)
        val lockUserSessionService = mock(LockUserSessionService::class.java)
        `when`(authorSessionService.findByName(tokenName))
            .thenThrow(RuntimeException("Session not found"))

        val jwt = createSignedJwt(tokenName, keyPair.private as ECPrivateKey)
        val result = verifyJwt(authorSessionService, lockUserSessionService, jwt)

        assertThat(result).isEqualTo(JwtValidationResult.Invalid)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun installBouncyCastle() {
            Security.addProvider(BouncyCastleProvider())
        }
    }
}
