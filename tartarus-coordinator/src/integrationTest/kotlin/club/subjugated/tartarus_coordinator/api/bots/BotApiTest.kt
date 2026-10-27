package club.subjugated.tartarus_coordinator.api.bots

import club.subjugated.fb.event.AcceptContract
import club.subjugated.fb.event.CommonMetadata
import club.subjugated.fb.event.Event
import club.subjugated.fb.event.EventType
import club.subjugated.fb.event.SignedEvent
import club.subjugated.fb.message.Bot
import club.subjugated.fb.message.Permission
import club.subjugated.fb.message.SignedMessage
import club.subjugated.tartarus_coordinator.TartarusCoordinatorApplication
import club.subjugated.tartarus_coordinator.api.BotController
import club.subjugated.tartarus_coordinator.api.BotCreateMessageRequest
import club.subjugated.tartarus_coordinator.api.messages.NewAuthorSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.NewBotMessage
import club.subjugated.tartarus_coordinator.api.messages.NewContractMessage
import club.subjugated.tartarus_coordinator.api.messages.NewLockSessionMessage
import club.subjugated.tartarus_coordinator.integration.config.IntegrationTestConfig
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.EventService
import club.subjugated.tartarus_coordinator.services.LockSessionService
import club.subjugated.tartarus_coordinator.util.derToRawSignature
import club.subjugated.tartarus_coordinator.util.deriveBotKeyPair
import club.subjugated.tartarus_coordinator.util.encodePublicKeySecp1
import club.subjugated.tartarus_coordinator.util.generateECKeyPair
import com.google.flatbuffers.FlatBufferBuilder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import kotlin.random.Random
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
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
class BotApiTest {
    @Autowired lateinit var botController: BotController
    @Autowired lateinit var authorSessionService: AuthorSessionService
    @Autowired lateinit var lockSessionService: LockSessionService
    @Autowired lateinit var eventService: EventService

    @Test
    fun testCreateContractAndEventDeliveryForOwnedBot() {
        val authorKeyPair = generateECKeyPair()
        val authorPubKeyB64 =
            Base64.getEncoder()
                .encodeToString(encodePublicKeySecp1(authorKeyPair.public as ECPublicKey))
        val authorSession =
            authorSessionService.saveNewAuthorSession(
                NewAuthorSessionMessage(
                    publicKey = authorPubKeyB64,
                    sessionToken = "tok-1",
                    signature = "",
                )
            )
        val authorPrincipal =
            User.withUsername(authorSession.name).password("").authorities(emptyList()).build()

        // Derive bot keypair deterministically from author keypair
        val botKeyPair = deriveBotKeyPair(authorKeyPair.private, "timer", 1)
        val compressedBotPubKey = encodePublicKeySecp1(botKeyPair.public as ECPublicKey)
        val compressedBotPubKeyB64 = Base64.getEncoder().encodeToString(compressedBotPubKey)

        val newBotMessage =
            NewBotMessage(
                publicKey = compressedBotPubKeyB64,
                description = "integration test bot",
                internalName = "timer",
                keyVersion = 1,
            )
        val response = botController.newBot(authorPrincipal, newBotMessage)
        assertThat(response.statusCode).isEqualTo(HttpStatusCode.valueOf(200))
        val botName = response.body!!.name!!

        // Create a LockSession with a real ECDSA keypair so SignedEvent verification succeeds
        val lockKeyPair = generateECKeyPair()
        val lockPubKeyB64 =
            Base64.getEncoder()
                .encodeToString(encodePublicKeySecp1(lockKeyPair.public as ECPublicKey))
        val sessionToken = "QAAAB3"
        val nlsm =
            NewLockSessionMessage(
                publicKey = lockPubKeyB64,
                sessionToken = sessionToken,
                userSessionPublicKey = "",
            )

        val lockSession = lockSessionService.createLockSession(nlsm)
        val shareToken = lockSession.shareToken!!

        val (signedContractBytes, serialNumber) =
            Helper.makeSignedContractBytes(
                botName,
                "",
                false,
                botKeyPair.private.encoded,
                compressedBotPubKey,
            )

        val createContractResponse =
            botController.createContractForBot(
                authorPrincipal,
                botName,
                NewContractMessage(
                    shareableToken = shareToken,
                    authorName = authorSession.name,
                    signedMessage = Base64.getEncoder().encodeToString(signedContractBytes),
                    notes = "",
                ),
            )

        assertThat(createContractResponse.statusCode).isEqualTo(HttpStatusCode.valueOf(200))
        val contractName = createContractResponse.body!!.name
        assertThat(contractName).isNotEmpty()
        assertThat(createContractResponse.body!!.authorSessionName).isEqualTo(authorSession.name)

        // Add a bot message via REST
        val msgResp =
            botController.addMessageForBot(
                authorPrincipal,
                botName,
                contractName,
                BotCreateMessageRequest("Timer started"),
            )
        assertThat(msgResp.statusCode).isEqualTo(HttpStatusCode.valueOf(200))

        // Process an incoming lock event and verify it is recorded for the bot
        val signedEventBytes =
            Helper.makeSignedLockEvent(
                sessionToken,
                serialNumber.toUShort(),
                1.toUShort(),
                EventType.AcceptContract,
                lockKeyPair.private.encoded,
            )
        val savedEvent = eventService.processIncomingLockEvent(sessionToken, signedEventBytes)
        assertThat(savedEvent).isNotNull

        val eventsResponse = botController.getEvents(botName, contractName, 0, 50)
        assertThat(eventsResponse.statusCode).isEqualTo(HttpStatusCode.valueOf(200))
        assertThat(eventsResponse.body!!.events).hasSize(1)
        assertThat(eventsResponse.body!!.events.first().eventType).isEqualTo("AcceptContract")
    }
}

class Helper {
    companion object {
        fun makeSignedContractBytes(
            botName: String,
            terms: String,
            temporaryUnlock: Boolean,
            privateKey: ByteArray,
            publicKey: ByteArray,
            emergencyUnlock: Boolean = true,
        ): Pair<ByteArray, Int> {
            val builder = FlatBufferBuilder(1024)

            Permission.startPermission(builder)
            Permission.addReceiveEvents(builder, true)
            Permission.addCanUnlock(builder, true)
            Permission.addCanRelease(builder, true)
            val permissionOffset = Permission.endPermission(builder)

            val botNameOffset1 = builder.createString(botName)
            val publicKeyOffset = builder.createByteVector(publicKey)

            Bot.startBot(builder)
            Bot.addName(builder, botNameOffset1)
            Bot.addPublicKey(builder, publicKeyOffset)
            Bot.addPermissions(builder, permissionOffset)
            val botOffset = Bot.endBot(builder)

            val termsOffset = builder.createString(terms)
            club.subjugated.fb.message.Contract.startBotsVector(builder, 1)
            builder.addOffset(botOffset)
            val botsVectorOffset = builder.endVector()

            val serialNumber = Random.nextInt(1, 1 shl 16)

            club.subjugated.fb.message.Contract.startContract(builder)
            club.subjugated.fb.message.Contract.addSerialNumber(builder, serialNumber.toUShort())
            club.subjugated.fb.message.Contract.addPublicKey(builder, publicKeyOffset)
            club.subjugated.fb.message.Contract.addTerms(builder, termsOffset)
            club.subjugated.fb.message.Contract.addIsTemporaryUnlockAllowed(
                builder,
                temporaryUnlock,
            )
            club.subjugated.fb.message.Contract.addIsEmergencyUnlockAllowed(
                builder,
                emergencyUnlock,
            )
            club.subjugated.fb.message.Contract.addBots(builder, botsVectorOffset)
            val contractOffset = club.subjugated.fb.message.Contract.endContract(builder)

            builder.finish(contractOffset)
            val contractBytes = builder.sizedByteArray()

            val start = contractBytes[0].toInt() and 0xFF
            val contractStart = contractBytes[start].toInt() and 0xFF
            val vtableStart = start - contractStart
            val payloadToHash = contractBytes.copyOfRange(vtableStart, contractBytes.size)
            val hash = MessageDigest.getInstance("SHA-256").digest(payloadToHash)

            val keySpec = PKCS8EncodedKeySpec(privateKey)
            val keyFactory = KeyFactory.getInstance("EC", "BC")
            val privKey = keyFactory.generatePrivate(keySpec)

            var signature =
                Signature.getInstance("SHA256withECDSA", "BC")
                    .apply {
                        initSign(privKey)
                        update(hash)
                    }
                    .sign()
            signature = derToRawSignature(signature)

            val signatureOffset = builder.createByteVector(signature)

            SignedMessage.startSignedMessage(builder)
            SignedMessage.addSignature(builder, signatureOffset)
            SignedMessage.addPayload(builder, contractOffset)
            SignedMessage.addPayloadType(
                builder,
                club.subjugated.fb.message.MessagePayload.Contract,
            )
            val signedMessageOffset = SignedMessage.endSignedMessage(builder)

            builder.finish(signedMessageOffset)
            return Pair(builder.sizedByteArray(), serialNumber)
        }

        fun makeSignedLockEvent(
            sessionToken: String,
            contractSerial: UShort,
            counter: UShort,
            eventType: UByte,
            lockPrivateKeyBytes: ByteArray,
        ): ByteArray {
            val builder = FlatBufferBuilder(1024)
            val lockSessionOffset = builder.createString(sessionToken)
            val metadataOffset =
                CommonMetadata.createCommonMetadata(
                    builder,
                    lockSessionOffset,
                    contractSerial,
                    0.toUShort(),
                    counter,
                    0,
                    0,
                )
            AcceptContract.startAcceptContract(builder)
            val acceptOffset = AcceptContract.endAcceptContract(builder)
            val eventOffset = Event.createEvent(builder, metadataOffset, eventType, acceptOffset)
            builder.finish(eventOffset)

            val eventBytes = builder.sizedByteArray()
            val start = eventBytes[0].toInt() and 0xFF
            val eventStart = eventBytes[start].toInt() and 0xFF
            val vtableStart = start - eventStart
            val payloadToHash = eventBytes.copyOfRange(vtableStart, eventBytes.size)
            val hash = MessageDigest.getInstance("SHA-256").digest(payloadToHash)

            val keySpec = PKCS8EncodedKeySpec(lockPrivateKeyBytes)
            val keyFactory = KeyFactory.getInstance("EC", "BC")
            val privKey = keyFactory.generatePrivate(keySpec)

            var sig =
                Signature.getInstance("SHA256withECDSA", "BC")
                    .apply {
                        initSign(privKey)
                        update(hash)
                    }
                    .sign()
            sig = derToRawSignature(sig)

            val signedBuilder = FlatBufferBuilder(1024)
            val signedLockSessionOffset = signedBuilder.createString(sessionToken)
            val signedMetadataOffset =
                CommonMetadata.createCommonMetadata(
                    signedBuilder,
                    signedLockSessionOffset,
                    contractSerial,
                    0.toUShort(),
                    counter,
                    0,
                    0,
                )
            AcceptContract.startAcceptContract(signedBuilder)
            val signedAcceptOffset = AcceptContract.endAcceptContract(signedBuilder)
            val signedEventPayloadOffset =
                Event.createEvent(signedBuilder, signedMetadataOffset, eventType, signedAcceptOffset)
            val sigOffset = signedBuilder.createByteVector(sig)
            val signedEventOffset =
                SignedEvent.createSignedEvent(signedBuilder, sigOffset, signedEventPayloadOffset)
            signedBuilder.finish(signedEventOffset)
            return signedBuilder.sizedByteArray()
        }
    }
}
