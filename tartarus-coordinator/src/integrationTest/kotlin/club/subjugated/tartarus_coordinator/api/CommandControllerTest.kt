package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.TartarusCoordinatorApplication
import club.subjugated.tartarus_coordinator.api.messages.NewAuthorSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.NewContractMessage
import club.subjugated.tartarus_coordinator.api.messages.NewLockSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.NewLockUserSessionMessage
import club.subjugated.tartarus_coordinator.integration.config.IntegrationTestConfig
import club.subjugated.tartarus_coordinator.integration.helpers.makeCreateContractCommand
import club.subjugated.tartarus_coordinator.services.LockSessionService
import club.subjugated.tartarus_coordinator.util.derToRawSignature
import club.subjugated.tartarus_coordinator.util.encodePublicKeySecp1
import club.subjugated.tartarus_coordinator.util.generateECKeyPair
import club.subjugated.tartarus_coordinator.util.generateId
import club.subjugated.tartarus_coordinator.util.signEcdsaSha256
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.userdetails.User
import org.springframework.test.annotation.Commit
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.security.interfaces.ECPublicKey
import java.util.*
import kotlin.test.Test

@SpringBootTest(classes = [TartarusCoordinatorApplication::class])
@TestPropertySource(locations = ["classpath:application-test.properties"])
@ContextConfiguration(classes = [IntegrationTestConfig::class])
@Transactional
@Commit
@Configuration()
class CommandControllerTest {
    @Autowired
    lateinit var lockSessionService: LockSessionService
    @Autowired
    lateinit var authorSessionController: AuthorSessionController
    @Autowired
    lateinit var contractController: ContractController
    @Autowired
    lateinit var lockUserSessionController : LockUserSessionController
    @Autowired
    lateinit var commandController: CommandController

    @Test
    fun testManualAcknowledgement() {
        val sessionToken = "QAAAB3"
        val nlsm =
            NewLockSessionMessage(
                publicKey = "BPNK1yPUgv8o6P3Y/e82FyrjlNfzcoA5OWZW/7vAxK3if7HC/tIy5jyMWXUCLxTpSpFlAxy9fcmEh0hcZN4ocgY=",
                sessionToken = sessionToken,
                userSessionPublicKey = ""
            )

        val lockSession = lockSessionService.createLockSession(nlsm)

        val keyPair = generateECKeyPair()
        val compressedPubKeyInBase64 = Base64.getEncoder().encodeToString(encodePublicKeySecp1(keyPair.public as ECPublicKey))

        val authorSessionToken = generateId()
        val signature = derToRawSignature(signEcdsaSha256(keyPair.private, authorSessionToken.toByteArray()))

        val newAuthorSessionMessage = NewAuthorSessionMessage(
            publicKey = compressedPubKeyInBase64,
            sessionToken = authorSessionToken,
            signature = Base64.getEncoder().encodeToString(signature)
        )
        val response = authorSessionController.saveAuthorSession(newAuthorSessionMessage)
        print(response)

        val pubKey = encodePublicKeySecp1(keyPair.public as ECPublicKey)

        val contract = makeCreateContractCommand(response.body!!.name!!,
            lockSession.shareToken!!,
            "Test terms",
            false,
            keyPair.private.encoded,
            pubKey,
            false)
        print(contract)

        val ncm = NewContractMessage(
            shareableToken = lockSession.shareToken!!,
            authorName = response.body!!.name,
            signedMessage = Base64.getEncoder().encodeToString(contract.messageBytes),
            notes = "Notes"
        )

        val newLockUserSessionMessage = NewLockUserSessionMessage(
            sessionToken = sessionToken,
            nonce = "MRkJneafXtcJKCnR",
            cipher = "_cGjTEwwVfgcZc-TuNfOyV4p00RR",
            lockUserSessionPublicKey = "AvlJtmzUZQEdSEt9x+ifMWx/0iDNwruaqbOIRUif9UJs"
        )

        val response2 = lockUserSessionController.createLockUserSession(newLockUserSessionMessage)

        val lockUser = User.withUsername(response2.body!!.name)
            .password("asdf")
            .build()
        val contractResponse = contractController.saveContract(lockUser, ncm)

        val contractApprovalResponse = contractController.approve(lockUser, contractResponse.body!!.name)
        assertThat(contractApprovalResponse.body!!.state).isEqualTo("ACCEPTED")

        val commands = commandController.getCommandsForContractForLockUser(lockUser, contractResponse.body!!.name)
        assertThat(commands.body!!.size).isEqualTo(1)

        val acceptContractCommand = commands.body!!.first()

        commandController.manuallyAcknowledgeCommand(lockUser, contractResponse.body!!.name, acceptContractCommand.name)

        val commandsPart2 = commandController.getCommandsForContractForLockUser(lockUser, contractResponse.body!!.name)
        assertThat(commandsPart2.body!!.first().state).isEqualTo("ACKNOWLEDGED")
    }
}