package club.subjugated.tartarus_coordinator.filters

import club.subjugated.tartarus_coordinator.models.AdminSession
import club.subjugated.tartarus_coordinator.models.AuthorSession
import club.subjugated.tartarus_coordinator.services.AdminSessionService
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.LockUserSessionService
import club.subjugated.tartarus_coordinator.util.encodePublicKeySecp1
import club.subjugated.tartarus_coordinator.util.generateECKeyPair
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import jakarta.persistence.PersistenceException
import java.security.Security
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

class WebUserAuthenticationFilterTest {

    private lateinit var authorSessionService: AuthorSessionService
    private lateinit var lockUserSessionService: LockUserSessionService
    private lateinit var adminSessionService: AdminSessionService
    private lateinit var filter: WebUserAuthenticationFilter

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        authorSessionService = mock(AuthorSessionService::class.java)
        lockUserSessionService = mock(LockUserSessionService::class.java)
        adminSessionService = mock(AdminSessionService::class.java)
        filter =
            WebUserAuthenticationFilter(
                authorSessionService,
                lockUserSessionService,
                adminSessionService,
            )
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun signJwt(subject: String, privateKey: ECPrivateKey): String {
        val claims = JWTClaimsSet.Builder().subject(subject).build()
        val signedJWT = SignedJWT(JWSHeader(JWSAlgorithm.ES256), claims)
        signedJWT.sign(ECDSASigner(privateKey))
        return signedJWT.serialize()
    }

    @Test
    fun testDoFilter_NoAuthHeaderPassesThroughWithoutAuth() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    @Test
    fun testDoFilter_MalformedJwtDoesNotThrow500() {
        val request =
            MockHttpServletRequest().apply { addHeader("Authorization", "Bearer not-a-valid-jwt") }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    @Test
    fun testDoFilter_ValidAuthorSessionSetsAuthWithoutAdmin() {
        val keyPair = generateECKeyPair()
        val pubKey =
            Base64.getEncoder().encodeToString(encodePublicKeySecp1(keyPair.public as ECPublicKey))
        val tokenName = "as-test-author"

        `when`(authorSessionService.findByName(tokenName))
            .thenReturn(AuthorSession(name = tokenName, publicKey = pubKey))

        val token = signJwt(tokenName, keyPair.private as ECPrivateKey)
        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer $token") }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        val auth = SecurityContextHolder.getContext().authentication
        assertThat(auth).isNotNull
        assertThat(auth!!.name).isEqualTo(tokenName)
        assertThat(auth.authorities.map { it.authority }).doesNotContain("ADMIN")
    }

    @Test
    fun testDoFilter_ValidAdminSessionSetsAdminAuthority() {
        val keyPair = generateECKeyPair()
        val pubKey =
            Base64.getEncoder().encodeToString(encodePublicKeySecp1(keyPair.public as ECPublicKey))
        val tokenName = "ad-test-admin"

        `when`(adminSessionService.findByName(tokenName))
            .thenReturn(AdminSession(name = tokenName, publicKey = pubKey))

        val token = signJwt(tokenName, keyPair.private as ECPrivateKey)
        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer $token") }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        val auth = SecurityContextHolder.getContext().authentication
        assertThat(auth).isNotNull
        assertThat(auth!!.name).isEqualTo(tokenName)
        assertThat(auth.authorities.map { it.authority }).contains("ADMIN")
    }

    @Test
    fun testDoFilter_PersistenceExceptionCaughtSafely() {
        val keyPair = generateECKeyPair()
        val tokenName = "as-nonexistent"

        `when`(authorSessionService.findByName(tokenName))
            .thenThrow(PersistenceException("Session not in DB"))

        val token = signJwt(tokenName, keyPair.private as ECPrivateKey)
        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer $token") }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun installBouncyCastle() {
            Security.addProvider(BouncyCastleProvider())
        }
    }
}
