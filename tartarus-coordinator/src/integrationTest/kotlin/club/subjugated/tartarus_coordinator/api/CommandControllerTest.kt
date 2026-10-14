package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.TartarusCoordinatorApplication
import club.subjugated.tartarus_coordinator.api.messages.NewAuthorSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.NewContractMessage
import club.subjugated.tartarus_coordinator.api.messages.NewLockSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.NewLockUserSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.AcknowledgeCommandMessage
import club.subjugated.tartarus_coordinator.integration.config.IntegrationTestConfig
import club.subjugated.tartarus_coordinator.integration.helpers.makeCreateContractCommand
import club.subjugated.tartarus_coordinator.integration.helpers.makeLoginCryptogram
import club.subjugated.tartarus_coordinator.integration.helpers.makeSignedAcknowledgement
import club.subjugated.tartarus_coordinator.integration.helpers.makeSignedError
import club.subjugated.tartarus_coordinator.services.ContractService
import club.subjugated.tartarus_coordinator.services.LockSessionService
import club.subjugated.tartarus_coordinator.util.derToRawSignature
import club.subjugated.tartarus_coordinator.util.encodePublicKeySecp1
import club.subjugated.tartarus_coordinator.util.generateECKeyPair
import club.subjugated.tartarus_coordinator.util.generateId
import club.subjugated.tartarus_coordinator.util.signEcdsaSha256
import java.security.interfaces.ECPublicKey
import java.util.*
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
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
class CommandControllerTest {
    @Autowired lateinit var lockSessionService: LockSessionService
    @Autowired lateinit var authorSessionController: AuthorSessionController
    @Autowired lateinit var contractController: ContractController
    @Autowired lateinit var lockUserSessionController: LockUserSessionController
    @Autowired lateinit var commandController: CommandController
    @Autowired lateinit var contractService: ContractService
    @org.springframework.beans.factory.annotation.Value("\${tartarus.login_token_private_key}")
    lateinit var tokenPrivateKey: String

    @Test
    fun testManualAcknowledgement() {
        val sessionToken = "QAAAB3"
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
        val response = authorSessionController.saveAuthorSession(newAuthorSessionMessage)
        print(response)

        val pubKey = encodePublicKeySecp1(keyPair.public as ECPublicKey)

        val contract =
            makeCreateContractCommand(
                response.body!!.name!!,
                lockSession.shareToken!!,
                "Test terms",
                false,
                keyPair.private.encoded,
                pubKey,
                false,
            )
        print(contract)

        val ncm =
            NewContractMessage(
                shareableToken = lockSession.shareToken!!,
                authorName = response.body!!.name,
                signedMessage = Base64.getEncoder().encodeToString(contract.messageBytes),
                notes = "Notes",
            )

        val newLockUserSessionMessage =
            NewLockUserSessionMessage(
                sessionToken = sessionToken,
                nonce = "MRkJneafXtcJKCnR",
                cipher = "_cGjTEwwVfgcZc-TuNfOyV4p00RR",
                lockUserSessionPublicKey = "AvlJtmzUZQEdSEt9x+ifMWx/0iDNwruaqbOIRUif9UJs",
            )

        val response2 = lockUserSessionController.createLockUserSession(newLockUserSessionMessage)

        val lockUser = User.withUsername(response2.body!!.name).password("asdf").build()
        val contractResponse = contractController.saveContract(lockUser, ncm)

        val contractApprovalResponse =
            contractController.approve(lockUser, contractResponse.body!!.name)
        assertThat(contractApprovalResponse.body!!.state).isEqualTo("ACCEPTED")

        val commands =
            commandController.getCommandsForContractForLockUser(
                lockUser,
                contractResponse.body!!.name,
            )
        assertThat(commands.body!!.size).isEqualTo(1)

        val acceptContractCommand = commands.body!!.first()

        commandController.manuallyAcknowledgeCommand(
            lockUser,
            contractResponse.body!!.name,
            acceptContractCommand.name,
        )

        val commandsPart2 =
            commandController.getCommandsForContractForLockUser(
                lockUser,
                contractResponse.body!!.name,
            )
        assertThat(commandsPart2.body!!.first().state).isEqualTo("ACKNOWLEDGED")
        assertThat(commandsPart2.body!!.first().isManuallyAcknowledged).isTrue()
    }

    @Test
    fun testCrossSessionCommandAccessDenied() {
        val sessionToken1 = "QAAAC1"
        val nlsm1 =
            NewLockSessionMessage(
                publicKey =
                    "BPNK1yPUgv8o6P3Y/e82FyrjlNfzcoA5OWZW/7vAxK3if7HC/tIy5jyMWXUCLxTpSpFlAxy9fcmEh0hcZN4ocgY=",
                sessionToken = sessionToken1,
                userSessionPublicKey = "",
            )
        val lockSession1 = lockSessionService.createLockSession(nlsm1)

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
        val contract =
            makeCreateContractCommand(
                authorResponse.body!!.name!!,
                lockSession1.shareToken!!,
                "Test terms",
                false,
                keyPair.private.encoded,
                encodePublicKeySecp1(keyPair.public as ECPublicKey),
                false,
            )
        val ncm =
            NewContractMessage(
                shareableToken = lockSession1.shareToken!!,
                authorName = authorResponse.body!!.name,
                signedMessage = Base64.getEncoder().encodeToString(contract.messageBytes),
                notes = "Notes",
            )

        val (nonce1, cipher1) =
            club.subjugated.tartarus_coordinator.integration.helpers.makeLoginCryptogram(
                tokenPrivateKey,
                lockSession1.decodePublicKey(),
                sessionToken1,
            )
        val userKeyPair1 = generateECKeyPair()
        val userPubKey1 =
            Base64.getEncoder()
                .encodeToString(encodePublicKeySecp1(userKeyPair1.public as ECPublicKey))
        val nlums1 =
            NewLockUserSessionMessage(
                sessionToken = sessionToken1,
                nonce = nonce1,
                cipher = cipher1,
                lockUserSessionPublicKey = userPubKey1,
            )
        val userSession1 = lockUserSessionController.createLockUserSession(nlums1)
        val lockUser1 = User.withUsername(userSession1.body!!.name).password("pass").build()
        val contractResponse = contractController.saveContract(lockUser1, ncm)
        contractController.approve(lockUser1, contractResponse.body!!.name)

        val commands =
            commandController.getCommandsForContractForLockUser(
                lockUser1,
                contractResponse.body!!.name,
            )
        val cmd = commands.body!!.first()

        // Create a completely separate lock session & user
        val sessionToken2 = "QAAAC2"
        val nlsm2 =
            NewLockSessionMessage(
                publicKey =
                    "BPNK1yPUgv8o6P3Y/e82FyrjlNfzcoA5OWZW/7vAxK3if7HC/tIy5jyMWXUCLxTpSpFlAxy9fcmEh0hcZN4ocgY=",
                sessionToken = sessionToken2,
                userSessionPublicKey = "",
            )
        val lockSession2 = lockSessionService.createLockSession(nlsm2)
        val (nonce2, cipher2) =
            club.subjugated.tartarus_coordinator.integration.helpers.makeLoginCryptogram(
                tokenPrivateKey,
                lockSession2.decodePublicKey(),
                sessionToken2,
            )
        val userKeyPair2 = generateECKeyPair()
        val userPubKey2 =
            Base64.getEncoder()
                .encodeToString(encodePublicKeySecp1(userKeyPair2.public as ECPublicKey))
        val nlums2 =
            NewLockUserSessionMessage(
                sessionToken = sessionToken2,
                nonce = nonce2,
                cipher = cipher2,
                lockUserSessionPublicKey = userPubKey2,
            )
        val userSession2 = lockUserSessionController.createLockUserSession(nlums2)
        val lockUser2 = User.withUsername(userSession2.body!!.name).password("pass").build()

        // User 2 tries to get commands for User 1's contract -> FORBIDDEN
        val ex1 =
            org.junit.jupiter.api.assertThrows<
                org.springframework.web.server.ResponseStatusException
            > {
                commandController.getCommandsForContractForLockUser(
                    lockUser2,
                    contractResponse.body!!.name,
                )
            }
        assertThat(ex1.statusCode).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN)

        // User 2 tries to manually acknowledge User 1's command -> FORBIDDEN
        val ex2 =
            org.junit.jupiter.api.assertThrows<
                org.springframework.web.server.ResponseStatusException
            > {
                commandController.manuallyAcknowledgeCommand(
                    lockUser2,
                    contractResponse.body!!.name,
                    cmd.name,
                )
            }
        assertThat(ex2.statusCode).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN)
    }

    @Test
    fun testSignedAcknowledgementViaWiredSerial() {
        val sessionToken = "QAAAW1"
        val lockKeyPair = generateECKeyPair()
        val lockPubKey = encodePublicKeySecp1(lockKeyPair.public as ECPublicKey)
        val lockPubKeyBase64 = Base64.getEncoder().encodeToString(lockPubKey)

        val nlsm =
            NewLockSessionMessage(
                publicKey = lockPubKeyBase64,
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
        val response = authorSessionController.saveAuthorSession(newAuthorSessionMessage)

        val pubKey = encodePublicKeySecp1(keyPair.public as ECPublicKey)

        val contract =
            makeCreateContractCommand(
                response.body!!.name!!,
                lockSession.shareToken!!,
                "Test terms",
                false,
                keyPair.private.encoded,
                pubKey,
                false,
            )

        val ncm =
            NewContractMessage(
                shareableToken = lockSession.shareToken!!,
                authorName = response.body!!.name,
                signedMessage = Base64.getEncoder().encodeToString(contract.messageBytes),
                notes = "Notes",
            )

        val (nonce, cipher) =
            makeLoginCryptogram(
                tokenPrivateKey,
                lockSession.decodePublicKey(),
                sessionToken,
            )
        val userKeyPair = generateECKeyPair()
        val userPubKey =
            Base64.getEncoder()
                .encodeToString(encodePublicKeySecp1(userKeyPair.public as ECPublicKey))
        val newLockUserSessionMessage =
            NewLockUserSessionMessage(
                sessionToken = sessionToken,
                nonce = nonce,
                cipher = cipher,
                lockUserSessionPublicKey = userPubKey,
            )

        val response2 = lockUserSessionController.createLockUserSession(newLockUserSessionMessage)

        val lockUser = User.withUsername(response2.body!!.name).password("asdf").build()
        val contractResponse = contractController.saveContract(lockUser, ncm)

        val contractApprovalResponse =
            contractController.approve(lockUser, contractResponse.body!!.name)
        assertThat(contractApprovalResponse.body!!.state).isEqualTo("ACCEPTED")

        val commands =
            commandController.getCommandsForContractForLockUser(
                lockUser,
                contractResponse.body!!.name,
            )
        assertThat(commands.body!!.size).isEqualTo(1)

        val acceptContractCommand = commands.body!!.first()

        val signedAckBytes =
            makeSignedAcknowledgement(
                sessionToken = sessionToken,
                serialNumber = acceptContractCommand.serialNumber!!,
                counter = 0,
                privateKey = lockKeyPair.private.encoded,
                publicKey = lockPubKey,
            )
        val signedAckBase64 = Base64.getEncoder().encodeToString(signedAckBytes)

        val ackMessage = AcknowledgeCommandMessage(signedAcknowledgement = signedAckBase64)
        val updatedCommandsResponse =
            commandController.manuallyAcknowledgeCommand(
                lockUser,
                contractResponse.body!!.name,
                acceptContractCommand.name,
                ackMessage,
            )

        val updatedCmd = updatedCommandsResponse.body!!.first { it.name == acceptContractCommand.name }
        assertThat(updatedCmd.state).isEqualTo("ACKNOWLEDGED")
        assertThat(updatedCmd.isManuallyAcknowledged).isFalse()

        val updatedContract = contractService.getByNameForAuthor(contractResponse.body!!.name)
        assertThat(updatedContract.state.name).isEqualTo("CONFIRMED")
    }

    @Test
    fun testSignedErrorViaWiredSerial() {
        val sessionToken = "QAAAE1"
        val lockKeyPair = generateECKeyPair()
        val lockPubKey = encodePublicKeySecp1(lockKeyPair.public as ECPublicKey)
        val lockPubKeyBase64 = Base64.getEncoder().encodeToString(lockPubKey)

        val nlsm =
            NewLockSessionMessage(
                publicKey = lockPubKeyBase64,
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
        val response = authorSessionController.saveAuthorSession(newAuthorSessionMessage)

        val pubKey = encodePublicKeySecp1(keyPair.public as ECPublicKey)

        val contract =
            makeCreateContractCommand(
                response.body!!.name!!,
                lockSession.shareToken!!,
                "Test terms",
                false,
                keyPair.private.encoded,
                pubKey,
                false,
            )

        val ncm =
            NewContractMessage(
                shareableToken = lockSession.shareToken!!,
                authorName = response.body!!.name,
                signedMessage = Base64.getEncoder().encodeToString(contract.messageBytes),
                notes = "Notes",
            )

        val (nonce, cipher) =
            makeLoginCryptogram(
                tokenPrivateKey,
                lockSession.decodePublicKey(),
                sessionToken,
            )
        val userKeyPair = generateECKeyPair()
        val userPubKey =
            Base64.getEncoder()
                .encodeToString(encodePublicKeySecp1(userKeyPair.public as ECPublicKey))
        val newLockUserSessionMessage =
            NewLockUserSessionMessage(
                sessionToken = sessionToken,
                nonce = nonce,
                cipher = cipher,
                lockUserSessionPublicKey = userPubKey,
            )

        val response2 = lockUserSessionController.createLockUserSession(newLockUserSessionMessage)

        val lockUser = User.withUsername(response2.body!!.name).password("asdf").build()
        val contractResponse = contractController.saveContract(lockUser, ncm)

        contractController.approve(lockUser, contractResponse.body!!.name)

        val commands =
            commandController.getCommandsForContractForLockUser(
                lockUser,
                contractResponse.body!!.name,
            )
        val acceptContractCommand = commands.body!!.first()

        val signedErrBytes =
            makeSignedError(
                sessionToken = sessionToken,
                serialNumber = acceptContractCommand.serialNumber!!,
                counter = 0,
                errorMessage = "Servo jammed",
                privateKey = lockKeyPair.private.encoded,
                publicKey = lockPubKey,
            )
        val signedErrBase64 = Base64.getEncoder().encodeToString(signedErrBytes)

        val ackMessage = AcknowledgeCommandMessage(signedAcknowledgement = signedErrBase64)
        val updatedCommandsResponse =
            commandController.manuallyAcknowledgeCommand(
                lockUser,
                contractResponse.body!!.name,
                acceptContractCommand.name,
                ackMessage,
            )

        val updatedCmd = updatedCommandsResponse.body!!.first { it.name == acceptContractCommand.name }
        assertThat(updatedCmd.state).isEqualTo("ERROR")
        assertThat(updatedCmd.message).isEqualTo("Servo jammed")
    }
}
