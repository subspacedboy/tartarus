package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.TartarusCoordinatorApplication
import club.subjugated.tartarus_coordinator.api.messages.NewAuthorSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.NewBotMessage
import club.subjugated.tartarus_coordinator.api.messages.SyncBotsRequest
import club.subjugated.tartarus_coordinator.api.messages.UpdateBotMessage
import club.subjugated.tartarus_coordinator.integration.config.IntegrationTestConfig
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.util.deriveBotKeyPair
import club.subjugated.tartarus_coordinator.util.encodePublicKeySecp1
import club.subjugated.tartarus_coordinator.util.generateECKeyPair
import java.security.interfaces.ECPublicKey
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatusCode
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
@Configuration()
class BotControllerTest() {
    @Autowired lateinit var botController: BotController
    @Autowired lateinit var authorSessionService: AuthorSessionService

    @Test
    fun createAndManageOwnedBotsWithDerivedKeys() {
        val authorKeyPair = generateECKeyPair()
        val authorPubKeyB64 =
            Base64.getEncoder()
                .encodeToString(encodePublicKeySecp1(authorKeyPair.public as ECPublicKey))
        val authorSession =
            authorSessionService.saveNewAuthorSession(
                NewAuthorSessionMessage(
                    publicKey = authorPubKeyB64,
                    sessionToken = "test-tok",
                    signature = "",
                )
            )
        val authorPrincipal =
            User.withUsername(authorSession.name).password("").authorities(emptyList()).build()

        // Derive deterministic key for "timer" bot
        val timerKeyPair = deriveBotKeyPair(authorKeyPair.private, "timer", 1)
        val compressedPubKeyInBase64 =
            Base64.getEncoder()
                .encodeToString(encodePublicKeySecp1(timerKeyPair.public as ECPublicKey))

        val newBotMessage =
            NewBotMessage(
                publicKey = compressedPubKeyInBase64,
                description = "integration test timer bot",
                internalName = "timer",
                keyVersion = 1,
            )
        val response = botController.newBot(authorPrincipal, newBotMessage)

        assertThat(response.statusCode).isEqualTo(HttpStatusCode.valueOf(200))
        assertThat(response.body!!.publicKey).isEqualTo(compressedPubKeyInBase64)
        assertThat(response.body!!.authorSessionName).isEqualTo(authorSession.name)
        assertThat(response.body!!.internalName).isEqualTo("timer")
        assertThat(response.body!!.keyVersion).isEqualTo(1)

        // Syncing multiple bots idempotently under the same author session
        val announcerKeyPair = deriveBotKeyPair(authorKeyPair.private, "announcer", 1)
        val announcerPubKeyB64 =
            Base64.getEncoder()
                .encodeToString(encodePublicKeySecp1(announcerKeyPair.public as ECPublicKey))
        val syncResponse =
            botController.syncBots(
                authorPrincipal,
                SyncBotsRequest(
                    listOf(
                        newBotMessage,
                        NewBotMessage(
                            publicKey = announcerPubKeyB64,
                            description = "announcer bot",
                            internalName = "announcer",
                            keyVersion = 1,
                        ),
                    )
                ),
            )
        assertThat(syncResponse.statusCode).isEqualTo(HttpStatusCode.valueOf(200))
        assertThat(syncResponse.body).hasSize(2)

        val myBotsResponse = botController.getMyBots(authorPrincipal)
        assertThat(myBotsResponse.body!!.map { it.internalName }).contains("timer", "announcer")

        // Rotate key to version 2
        val timerKeyPairV2 = deriveBotKeyPair(authorKeyPair.private, "timer", 2)
        val timerV2PubKeyB64 =
            Base64.getEncoder()
                .encodeToString(encodePublicKeySecp1(timerKeyPairV2.public as ECPublicKey))
        val updated =
            botController.updateBot(
                authorPrincipal,
                response.body!!.name!!,
                UpdateBotMessage(publicKey = timerV2PubKeyB64, keyVersion = 2),
            )
        assertThat(updated.body!!.keyVersion).isEqualTo(2)
        assertThat(updated.body!!.publicKey).isEqualTo(timerV2PubKeyB64)

        val eventsResponse = botController.getEvents(response.body!!.name!!, "test-contract", 0, 50)
        assertThat(eventsResponse.statusCode).isEqualTo(HttpStatusCode.valueOf(200))
        assertThat(eventsResponse.body!!.events).isEmpty()
    }
}
