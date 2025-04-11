package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.TartarusCoordinatorApplication
import club.subjugated.tartarus_coordinator.api.messages.NewLockSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.NewLockUserSessionMessage
import club.subjugated.tartarus_coordinator.integration.config.IntegrationTestConfig
import club.subjugated.tartarus_coordinator.services.LockSessionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatusCode
import org.springframework.test.annotation.Commit
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.util.Base64

@SpringBootTest(classes = [TartarusCoordinatorApplication::class])
@TestPropertySource(locations = ["classpath:application-test.properties"])
@ContextConfiguration(classes = [IntegrationTestConfig::class])
@Transactional
@Commit
@Configuration()
class LockUserSessionControllerTest {
    @Autowired
    lateinit var lockUserSessionController: LockUserSessionController
    @Autowired
    lateinit var lockSessionService: LockSessionService

    @Test
    fun testLockStart() {
        val sessionToken = "QAAAB3"
        val nlsm =
            NewLockSessionMessage(
                publicKey = "BPNK1yPUgv8o6P3Y/e82FyrjlNfzcoA5OWZW/7vAxK3if7HC/tIy5jyMWXUCLxTpSpFlAxy9fcmEh0hcZN4ocgY=",
                sessionToken = sessionToken,
                userSessionPublicKey = ""
            )

        val lockSession = lockSessionService.createLockSession(nlsm)

        val newLockUserSessionMessage = NewLockUserSessionMessage(
            sessionToken = sessionToken,
            nonce = "MRkJneafXtcJKCnR",
            cipher = "_cGjTEwwVfgcZc-TuNfOyV4p00RR",
            lockUserSessionPublicKey = "AvlJtmzUZQEdSEt9x+ifMWx/0iDNwruaqbOIRUif9UJs"
        )

        val response = lockUserSessionController.createLockUserSession(newLockUserSessionMessage)
        assertThat(response.statusCode).isEqualTo(HttpStatusCode.valueOf(200))
        assertThat(response.body!!.lockSession!!.name).isEqualTo(lockSession.name)
    }
}