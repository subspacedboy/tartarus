package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.TartarusCoordinatorApplication
import club.subjugated.tartarus_coordinator.api.messages.NewAuthorSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.NewContractMessage
import club.subjugated.tartarus_coordinator.api.messages.NewLockSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.NewLockUserSessionMessage
import club.subjugated.tartarus_coordinator.integration.config.IntegrationTestConfig
import club.subjugated.tartarus_coordinator.integration.helpers.makeCreateContractCommand
import club.subjugated.tartarus_coordinator.models.ContractState
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
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.core.userdetails.User
import org.springframework.test.annotation.Commit
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@SpringBootTest(classes = [TartarusCoordinatorApplication::class])
@TestPropertySource(locations = ["classpath:application-test.properties"])
@ContextConfiguration(classes = [IntegrationTestConfig::class])
@Transactional
@Commit
@Configuration
class ContractControllerTest {

    @Autowired lateinit var lockSessionService: LockSessionService
    @Autowired lateinit var authorSessionController: AuthorSessionController
    @Autowired lateinit var contractController: ContractController
    @Autowired lateinit var lockUserSessionController: LockUserSessionController
    @org.springframework.beans.factory.annotation.Value("\${tartarus.login_token_private_key}")
    lateinit var tokenPrivateKey: String

    private fun createContractHelper(
        sessionToken: String
    ): Triple<
        org.springframework.security.core.userdetails.UserDetails,
        org.springframework.security.core.userdetails.UserDetails,
        String,
    > {
        val nlsm =
            NewLockSessionMessage(
                publicKey =
                    "BPNK1yPUgv8o6P3Y/e82FyrjlNfzcoA5OWZW/7vAxK3if7HC/tIy5jyMWXUCLxTpSpFlAxy9fcmEh0hcZN4ocgY=",
                sessionToken = sessionToken,
                userSessionPublicKey = "",
            )
        val lockSession = lockSessionService.createLockSession(nlsm)

        val keyPair = generateECKeyPair()
        val compressedPubKeyInBase64 =
            Base64.getEncoder().encodeToString(encodePublicKeySecp1(keyPair.public as ECPublicKey))
        val authorSessionToken = generateId()
        val signature =
            derToRawSignature(signEcdsaSha256(keyPair.private, authorSessionToken.toByteArray()))

        val newAuthorSessionMessage =
            NewAuthorSessionMessage(
                publicKey = compressedPubKeyInBase64,
                sessionToken = authorSessionToken,
                signature = Base64.getEncoder().encodeToString(signature),
            )
        val authorResponse = authorSessionController.saveAuthorSession(newAuthorSessionMessage)
        val authorUser = User.withUsername(authorResponse.body!!.name).password("").build()

        val contract =
            makeCreateContractCommand(
                authorResponse.body!!.name!!,
                lockSession.shareToken!!,
                "Test terms",
                false,
                keyPair.private.encoded,
                encodePublicKeySecp1(keyPair.public as ECPublicKey),
                false,
            )

        val ncm =
            NewContractMessage(
                shareableToken = lockSession.shareToken!!,
                authorName = authorResponse.body!!.name,
                signedMessage = Base64.getEncoder().encodeToString(contract.messageBytes),
                notes = "Notes",
            )

        val (nonce, cipher) =
            club.subjugated.tartarus_coordinator.integration.helpers.makeLoginCryptogram(
                tokenPrivateKey,
                lockSession.decodePublicKey(),
                sessionToken,
            )

        val userKeyPair = generateECKeyPair()
        val userPubKeyBase64 =
            Base64.getEncoder()
                .encodeToString(encodePublicKeySecp1(userKeyPair.public as ECPublicKey))

        val nlums =
            NewLockUserSessionMessage(
                sessionToken = sessionToken,
                nonce = nonce,
                cipher = cipher,
                lockUserSessionPublicKey = userPubKeyBase64,
            )
        val lockUserResponse = lockUserSessionController.createLockUserSession(nlums)
        val lockUser = User.withUsername(lockUserResponse.body!!.name).password("pass").build()

        val contractResponse = contractController.saveContract(lockUser, ncm)
        return Triple(lockUser, authorUser, contractResponse.body!!.name!!)
    }

    @Test
    fun testSaveAndApproveContractFlow() {
        val (lockUser, authorUser, contractName) = createContractHelper("CTOK1")

        // Contract should initially be review pending
        val reviewPending = contractController.getForReview(lockUser)
        assertThat(reviewPending.body!!.map { it.name }).contains(contractName)

        // Lock user list of contracts contains this contract
        val allContracts = contractController.getContractsForLockSession(lockUser)
        assertThat(allContracts.body!!.map { it.name }).contains(contractName)

        // Approve contract
        val approval = contractController.approve(lockUser, contractName)
        assertThat(approval.body!!.state).isEqualTo(ContractState.ACCEPTED.name)

        // No longer in review pending
        val reviewPendingAfter = contractController.getForReview(lockUser)
        assertThat(reviewPendingAfter.body!!.map { it.name }).doesNotContain(contractName)

        // Author can query contract by name
        val fetchedContract = contractController.getContractsByName(authorUser, contractName)
        assertThat(fetchedContract.body!!.name).isEqualTo(contractName)
    }

    @Test
    fun testSaveAndRejectContractFlow() {
        val (lockUser, _, contractName) = createContractHelper("CTOK2")

        val rejection = contractController.reject(lockUser, contractName)
        assertThat(rejection.body!!.state).isEqualTo(ContractState.REJECTED.name)

        val reviewPendingAfter = contractController.getForReview(lockUser)
        assertThat(reviewPendingAfter.body!!.map { it.name }).doesNotContain(contractName)
    }

    @Test
    fun testCrossSessionApproveAndRejectDenied() {
        val (_, _, contractName1) = createContractHelper("CTOK3")
        val (lockUser2, _, _) = createContractHelper("CTOK4")

        // User 2 trying to approve User 1's contract -> 403 Forbidden
        val approveEx =
            assertThrows<ResponseStatusException> {
                contractController.approve(lockUser2, contractName1)
            }
        assertThat(approveEx.statusCode).isEqualTo(HttpStatus.FORBIDDEN)

        // User 2 trying to reject User 1's contract -> 403 Forbidden
        val rejectEx =
            assertThrows<ResponseStatusException> {
                contractController.reject(lockUser2, contractName1)
            }
        assertThat(rejectEx.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun testApproveAndRejectNonExistentContract() {
        val (lockUser, _, _) = createContractHelper("CTOK5")

        val approveEx =
            assertThrows<ResponseStatusException> {
                contractController.approve(lockUser, "c-does-not-exist")
            }
        assertThat(approveEx.statusCode).isEqualTo(HttpStatus.NOT_FOUND)

        val rejectEx =
            assertThrows<ResponseStatusException> {
                contractController.reject(lockUser, "c-does-not-exist")
            }
        assertThat(rejectEx.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }
}
