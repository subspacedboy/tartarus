package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.TartarusCoordinatorApplication
import club.subjugated.tartarus_coordinator.api.messages.NewAuthorSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.NewLockSessionMessage
import club.subjugated.tartarus_coordinator.integration.config.IntegrationTestConfig
import club.subjugated.tartarus_coordinator.services.LockSessionService
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
import org.springframework.security.core.userdetails.User
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
class LockSessionControllerTest {

    @Autowired lateinit var authorSessionController: AuthorSessionController
    @Autowired lateinit var lockSessionController: LockSessionController
    @Autowired lateinit var lockSessionService: LockSessionService

    @Test
    fun testDeleteKnownToken_Success() {
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
        val authorResponse = authorSessionController.saveAuthorSession(message)
        val authorUser = User.withUsername(authorResponse.body!!.name).password("").build()

        val lockSessionToken = generateId()
        val nlsm =
            NewLockSessionMessage(
                publicKey =
                    "BPNK1yPUgv8o6P3Y/e82FyrjlNfzcoA5OWZW/7vAxK3if7HC/tIy5jyMWXUCLxTpSpFlAxy9fcmEh0hcZN4ocgY=",
                sessionToken = lockSessionToken,
                userSessionPublicKey = "",
            )
        val lockSession = lockSessionService.createLockSession(nlsm)

        // Author discovers/visits the shareable token
        val sessionResp = lockSessionController.findLockSession(authorUser, lockSession.shareToken!!)
        assertThat(sessionResp.statusCode).isEqualTo(HttpStatus.OK)

        // Known tokens should contain this token
        val knownTokensResp = lockSessionController.findKnownTokens(authorUser)
        assertThat(knownTokensResp.statusCode).isEqualTo(HttpStatus.OK)
        val knownTokens = knownTokensResp.body!!
        assertThat(knownTokens).hasSize(1)
        val tokenToDelete = knownTokens.first()
        assertThat(tokenToDelete.shareableToken).isEqualTo(lockSession.shareToken)

        // Delete the known token
        val deleteResp = lockSessionController.deleteKnownToken(authorUser, tokenToDelete.name!!)
        assertThat(deleteResp.statusCode).isEqualTo(HttpStatus.OK)

        // Verify it is no longer returned in known tokens
        val afterDeleteResp = lockSessionController.findKnownTokens(authorUser)
        assertThat(afterDeleteResp.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(afterDeleteResp.body!!).isEmpty()

        // If author visits the shareable token again, it is revived
        val reVisitResp = lockSessionController.findLockSession(authorUser, lockSession.shareToken!!)
        assertThat(reVisitResp.statusCode).isEqualTo(HttpStatus.OK)

        val reRevivedResp = lockSessionController.findKnownTokens(authorUser)
        assertThat(reRevivedResp.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(reRevivedResp.body!!).hasSize(1)
        assertThat(reRevivedResp.body!!.first().name).isEqualTo(tokenToDelete.name)
    }
}
