package club.subjugated.tartarus_coordinator.api.bots

import club.subjugated.fb.bots.BotApiMessage
import club.subjugated.fb.bots.CreateContractRequest
import club.subjugated.fb.bots.CreateContractResponse
import club.subjugated.fb.bots.MessagePayload
import club.subjugated.fb.event.CommonMetadata
import club.subjugated.fb.event.Event
import club.subjugated.fb.event.EventType
import club.subjugated.fb.event.SignedEvent
import club.subjugated.fb.message.Bot
import club.subjugated.fb.message.Permission
import club.subjugated.fb.message.SignedMessage
import club.subjugated.tartarus_coordinator.TartarusCoordinatorApplication
import club.subjugated.tartarus_coordinator.api.BotController
import club.subjugated.tartarus_coordinator.api.messages.NewBotMessage
import club.subjugated.tartarus_coordinator.api.messages.NewLockSessionMessage
import club.subjugated.tartarus_coordinator.integration.config.IntegrationTestConfig
import club.subjugated.tartarus_coordinator.services.LockSessionService
import club.subjugated.tartarus_coordinator.util.derToRawSignature
import club.subjugated.tartarus_coordinator.util.encodePublicKeySecp1
import club.subjugated.tartarus_coordinator.util.generateECKeyPair
import com.google.flatbuffers.FlatBufferBuilder
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatusCode
import org.springframework.test.annotation.Commit
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(classes = [TartarusCoordinatorApplication::class])
@TestPropertySource(locations = ["classpath:application-test.properties"])
@ContextConfiguration(classes = [IntegrationTestConfig::class])
@Transactional
@Commit
@Configuration()
class BotApiTest {
    @Autowired lateinit var botApiController: BotApiController

    @Autowired lateinit var botController: BotController
    @Autowired lateinit var lockSessionService: LockSessionService

    @Test
    fun testCreateContractRequest() {
        // We need a bot.
        val keyPair = generateECKeyPair()
        val compressedPubKeyInBase64 =
            Base64.getEncoder().encodeToString(encodePublicKeySecp1(keyPair.public as ECPublicKey))

        val newBotMessage =
            NewBotMessage(
                publicKey = compressedPubKeyInBase64,
                description = "integration test bot",
            )
        val response = botController.newBot(newBotMessage)
        assertThat(response.statusCode).isEqualTo(HttpStatusCode.valueOf(200))
        val botName = response.body!!.name

        // We need a LockSession
        val sessionToken = "QAAAB3"
        val nlsm =
            NewLockSessionMessage(
                publicKey =
                    "BPNK1yPUgv8o6P3Y/e82FyrjlNfzcoA5OWZW/7vAxK3if7HC/tIy5jyMWXUCLxTpSpFlAxy9fcmEh0hcZN4ocgY=",
                sessionToken = sessionToken,
                userSessionPublicKey = "",
            )

        val lockSession = lockSessionService.createLockSession(nlsm)
        val shareToken = lockSession.shareToken

        val message =
            Helper.makeCreateContractBotApiMessage(
                botName!!,
                shareToken!!,
                "",
                false,
                keyPair.private.encoded,
                encodePublicKeySecp1(keyPair.public as ECPublicKey),
                false,
            )

        val apiMessage = BotApiMessage.getRootAsBotApiMessage(ByteBuffer.wrap(message))
        val apiResponse = botApiController.routeRequest(apiMessage)

        val response2: CreateContractResponse =
            when (apiResponse.payloadType) {
                MessagePayload.CreateContractResponse -> {
                    val response = CreateContractResponse()
                    apiResponse.payload(response)
                    response
                }
                else -> {
                    throw IllegalStateException()
                }
            }

        assertThat(response2.contractName).isNotEmpty()

        // Test GetEventsRequest FlatBuffers API routing
        val builder = FlatBufferBuilder(1024)
        val contractNameOffset = builder.createString(response2.contractName)
        val reqOffset =
            club.subjugated.fb.bots.GetEventsRequest.createGetEventsRequest(
                builder,
                contractNameOffset,
                0.toUShort(),
                50.toUShort(),
            )
        val botNameOffset = builder.createString(botName)
        val requestId = Random.nextLong(Long.MAX_VALUE)

        BotApiMessage.startBotApiMessage(builder)
        BotApiMessage.addName(builder, botNameOffset)
        BotApiMessage.addPayloadType(builder, MessagePayload.GetEventsRequest)
        BotApiMessage.addPayload(builder, reqOffset)
        BotApiMessage.addRequestId(builder, requestId)
        val botApiMessageOffset = BotApiMessage.endBotApiMessage(builder)
        builder.finish(botApiMessageOffset)

        val getEventsApiMessage =
            BotApiMessage.getRootAsBotApiMessage(ByteBuffer.wrap(builder.sizedByteArray()))
        val getEventsApiResponse = botApiController.routeRequest(getEventsApiMessage)

        assertThat(getEventsApiResponse.payloadType).isEqualTo(MessagePayload.GetEventsResponse)
        val getEventsResponse = club.subjugated.fb.bots.GetEventsResponse()
        getEventsApiResponse.payload(getEventsResponse)
        assertThat(getEventsResponse.eventsLength).isEqualTo(0)
        assertThat(getEventsResponse.hasMore).isFalse()
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun testEmergencyUnlockEventPropagation() {
        // 1. Create a bot
        val keyPair = generateECKeyPair()
        val compressedPubKeyInBase64 =
            Base64.getEncoder().encodeToString(encodePublicKeySecp1(keyPair.public as ECPublicKey))

        val newBotMessage =
            NewBotMessage(
                publicKey = compressedPubKeyInBase64,
                description = "emergency unlock test bot",
            )
        val botResponse = botController.newBot(newBotMessage)
        assertThat(botResponse.statusCode).isEqualTo(HttpStatusCode.valueOf(200))
        val botName = botResponse.body!!.name!!
        val botPassword = botResponse.body!!.clearTextPassword!!

        // 2. Create a lock session
        val sessionToken = "EMG001"
        val nlsm =
            NewLockSessionMessage(
                publicKey =
                    "BPNK1yPUgv8o6P3Y/e82FyrjlNfzcoA5OWZW/7vAxK3if7HC/tIy5jyMWXUCLxTpSpFlAxy9fcmEh0hcZN4ocgY=",
                sessionToken = sessionToken,
                userSessionPublicKey = "",
            )
        lockSessionService.createLockSession(nlsm)

        // 3. Connect bot MQTT client and subscribe to inbox_events
        val brokerUrl = "tcp://localhost:1884"
        val botClient = MqttClient(brokerUrl, botName, null)
        val botOptions =
            MqttConnectOptions().apply {
                userName = botName
                password = botPassword.toCharArray()
                isCleanSession = true
            }
        botClient.connect(botOptions)

        val receivedEventFuture = CompletableFuture<SignedEvent>()
        val eventsTopic = "bots/inbox_events_${botName}"
        botClient.subscribe(eventsTopic) { _, message ->
            try {
                val signedEvent = SignedEvent.getRootAsSignedEvent(ByteBuffer.wrap(message.payload))
                receivedEventFuture.complete(signedEvent)
            } catch (t: Throwable) {
                receivedEventFuture.completeExceptionally(t)
            }
        }

        // 4. Connect lock MQTT client
        val lockClient = MqttClient(brokerUrl, sessionToken, null)
        val lockOptions =
            MqttConnectOptions().apply {
                userName = sessionToken
                password = sessionToken.toCharArray()
                isCleanSession = true
            }
        lockClient.connect(lockOptions)

        // 5. Build SignedEvent with EventType.EmergencyUnlock
        val contractSerial = 42.toUShort()
        val builder = FlatBufferBuilder(1024)
        val lockSessionOffset = builder.createString(sessionToken)
        val metadataOffset =
            CommonMetadata.createCommonMetadata(
                builder,
                lockSessionOffset,
                contractSerial,
                0.toUShort(),
                0.toUShort(),
                0,
                0,
            )
        val eventOffset = Event.createEvent(builder, metadataOffset, EventType.EmergencyUnlock)
        val dummySig = ByteArray(64)
        val sigOffset = builder.createByteVector(dummySig)
        val signedEventOffset = SignedEvent.createSignedEvent(builder, sigOffset, eventOffset)
        builder.finish(signedEventOffset)
        val payloadBytes = builder.sizedByteArray()

        // 6. Lock publishes to bot's inbox_events topic
        lockClient.publish(eventsTopic, MqttMessage(payloadBytes))

        // 7. Await bot receiving the event and assert payload
        val receivedSignedEvent = receivedEventFuture.get(5, TimeUnit.SECONDS)
        assertThat(receivedSignedEvent).isNotNull
        assertThat(receivedSignedEvent.payload).isNotNull
        assertThat(receivedSignedEvent.payload!!.eventType).isEqualTo(EventType.EmergencyUnlock)
        assertThat(receivedSignedEvent.payload!!.metadata).isNotNull
        assertThat(receivedSignedEvent.payload!!.metadata!!.lockSession).isEqualTo(sessionToken)
        assertThat(receivedSignedEvent.payload!!.metadata!!.contractSerialNumber)
            .isEqualTo(contractSerial)

        // Clean up
        lockClient.disconnect()
        lockClient.close()
        botClient.disconnect()
        botClient.close()
    }
}

class Helper {
    companion object {
        fun makeCreateContractBotApiMessage(
            botName: String,
            shareableToken: String,
            terms: String,
            temporaryUnlock: Boolean,
            privateKey: ByteArray,
            publicKey: ByteArray,
            public: Boolean,
            emergencyUnlock: Boolean = true,
        ): ByteArray {
            val builder = FlatBufferBuilder(1024)

            var announcerBotOffset: Int? = 0

            //        if(public) {
            //            val announcerBotMap = botMapService.getBotMap("announcer", coordinator)
            //
            //            // Build permission for Announcer
            //            Permission.startPermission(builder)
            //            Permission.addReceiveEvents(builder, true)
            //            Permission.addCanUnlock(builder, false)
            //            Permission.addCanRelease(builder, false)
            //            val announcerPermissionOffset = Permission.endPermission(builder)
            //
            //            val announcerBotName = builder.createString(announcerBotMap.externalName)
            //            val compressedPublicKeyBytes =
            // encodePublicKeySecp1(loadECPublicKeyFromPkcs8(announcerBotMap.publicKey!!) as
            // ECPublicKey)
            //            val pubKeyOffset = builder.createByteVector(compressedPublicKeyBytes)
            //
            //            Bot.startBot(builder)
            //            Bot.addName(builder, announcerBotName)
            //            Bot.addPublicKey(builder, pubKeyOffset)
            //            Bot.addPermissions(builder, announcerPermissionOffset)
            //            announcerBotOffset = Bot.endBot(builder)
            //        }

            // Build Permission
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
            val botsVectorOffset =
                if (public) {
                    club.subjugated.fb.message.Contract.startBotsVector(builder, 2)
                    builder.addOffset(botOffset)
                    builder.addOffset(announcerBotOffset!!)
                    builder.endVector()
                } else {
                    club.subjugated.fb.message.Contract.startBotsVector(builder, 1)
                    builder.addOffset(botOffset)
                    builder.endVector()
                }

            val serialNumber = Random.nextInt(0, 1 shl 16)

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

            // Hash payload
            val start = contractBytes[0].toInt() and 0xFF
            val contractStart = contractBytes[start].toInt() and 0xFF
            val vtableStart = start - contractStart
            val payloadToHash = contractBytes.copyOfRange(vtableStart, contractBytes.size)
            val hash = MessageDigest.getInstance("SHA-256").digest(payloadToHash)

            val keySpec = PKCS8EncodedKeySpec(privateKey)
            val keyFactory = KeyFactory.getInstance("EC", "BC")
            val privateKey = keyFactory.generatePrivate(keySpec)

            var signature =
                Signature.getInstance("SHA256withECDSA", "BC")
                    .apply {
                        initSign(privateKey)
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
            // NB: Even if a bot is the issuer of the contract we don't set the authority identifier
            // because that will break contract acceptance. The lock will try and validate against a
            // key
            // it doesn't have.
            // SignedMessage.addAuthorityIdentifier(builder, botAuthorityIdentifer)
            val signedMessageOffset = SignedMessage.endSignedMessage(builder)

            builder.finish(signedMessageOffset)
            val signedMessageBytes = builder.sizedByteArray()

            // --- Build CreateCommandRequest ---
            val builder3 = FlatBufferBuilder(1024)
            val contractAsSignedMessageOffset = builder3.createByteVector(signedMessageBytes)

            val shareableTokenOffset = builder3.createString(shareableToken)

            CreateContractRequest.startCreateContractRequest(builder3)
            CreateContractRequest.addShareableToken(builder3, shareableTokenOffset)
            CreateContractRequest.addContract(builder3, contractAsSignedMessageOffset)
            val createContractOffset = CreateContractRequest.endCreateContractRequest(builder3)

            // --- Build BotApiMessage ---
            val botNameOffset = builder3.createString(botName)
            val requestId = Random.nextLong(Long.MAX_VALUE)

            BotApiMessage.startBotApiMessage(builder3)
            BotApiMessage.addName(builder3, botNameOffset)
            BotApiMessage.addPayloadType(builder3, MessagePayload.CreateContractRequest)
            BotApiMessage.addPayload(builder3, createContractOffset)
            BotApiMessage.addRequestId(builder3, requestId)
            val botApiMessageOffset = BotApiMessage.endBotApiMessage(builder3)

            builder3.finish(botApiMessageOffset)
            return builder3.sizedByteArray()
        }
    }
}
