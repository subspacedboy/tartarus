package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.TartarusCoordinatorApplication
import club.subjugated.tartarus_coordinator.api.messages.NewAuthorSessionMessage
import club.subjugated.tartarus_coordinator.integration.config.IntegrationTestConfig
import club.subjugated.tartarus_coordinator.util.derToRawSignature
import club.subjugated.tartarus_coordinator.util.encodePublicKeySecp1
import club.subjugated.tartarus_coordinator.util.generateECKeyPair
import club.subjugated.tartarus_coordinator.util.generateId
import club.subjugated.tartarus_coordinator.util.signEcdsaSha256
import java.security.interfaces.ECPublicKey
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.test.annotation.Commit
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(classes = [TartarusCoordinatorApplication::class])
@TestPropertySource(locations = ["classpath:application-test.properties"])
@ContextConfiguration(classes = [IntegrationTestConfig::class])
@Transactional
@Commit
@Configuration
class AuthorSessionControllerTest {

    @Autowired lateinit var authorSessionController: AuthorSessionController

    @Test
    fun testSaveAuthorSession_Success() {
        val keyPair = generateECKeyPair()
        val compressedPubKeyInBase64 =
            Base64.getEncoder().encodeToString(encodePublicKeySecp1(keyPair.public as ECPublicKey))
        val sessionToken = generateId()
        val signature =
            derToRawSignature(signEcdsaSha256(keyPair.private, sessionToken.toByteArray()))

        val message =
            NewAuthorSessionMessage(
                publicKey = compressedPubKeyInBase64,
                sessionToken = sessionToken,
                signature = Base64.getEncoder().encodeToString(signature),
            )

        val response = authorSessionController.saveAuthorSession(message)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!.name).startsWith("as-")
    }

    @Test
    fun testSaveAuthorSession_InvalidSignatureReturnsBadRequest() {
        val keyPair = generateECKeyPair()
        val wrongKeyPair = generateECKeyPair()
        val compressedPubKeyInBase64 =
            Base64.getEncoder().encodeToString(encodePublicKeySecp1(keyPair.public as ECPublicKey))
        val sessionToken = generateId()
        // Sign with the wrong private key
        val wrongSignature =
            derToRawSignature(signEcdsaSha256(wrongKeyPair.private, sessionToken.toByteArray()))

        val message =
            NewAuthorSessionMessage(
                publicKey = compressedPubKeyInBase64,
                sessionToken = sessionToken,
                signature = Base64.getEncoder().encodeToString(wrongSignature),
            )

        val response = authorSessionController.saveAuthorSession(message)
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }
}
