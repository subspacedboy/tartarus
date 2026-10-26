package club.subjugated.tartarus_coordinator.services

import club.subjugated.fb.event.CommonMetadata
import club.subjugated.fb.event.EmergencyUnlock
import club.subjugated.fb.event.Event
import club.subjugated.fb.event.EventType
import club.subjugated.fb.event.PeriodicUpdate
import club.subjugated.fb.event.SignedEvent
import club.subjugated.fb.event.StartedUpdate
import club.subjugated.fb.event.Unlock
import club.subjugated.fb.message.Bot as FbBot
import club.subjugated.fb.message.Contract as FbContract
import club.subjugated.fb.message.MessagePayload
import club.subjugated.fb.message.Permission
import club.subjugated.fb.message.SignedMessage
import club.subjugated.tartarus_coordinator.api.messages.NewLockSessionMessage
import club.subjugated.tartarus_coordinator.components.EventWebSocketHandler
import club.subjugated.tartarus_coordinator.models.AuthorSession
import club.subjugated.tartarus_coordinator.models.Bot
import club.subjugated.tartarus_coordinator.models.BotState
import club.subjugated.tartarus_coordinator.models.Contract
import club.subjugated.tartarus_coordinator.models.ContractState
import club.subjugated.tartarus_coordinator.models.LockSession
import club.subjugated.tartarus_coordinator.storage.BotRepository
import club.subjugated.tartarus_coordinator.storage.ContractRepository
import club.subjugated.tartarus_coordinator.storage.EventDeliveryRepository
import club.subjugated.tartarus_coordinator.storage.EventRepository
import club.subjugated.tartarus_coordinator.util.derToRawSignature
import club.subjugated.tartarus_coordinator.util.encodePublicKeySecp1
import club.subjugated.tartarus_coordinator.util.generateECKeyPair
import com.google.flatbuffers.FlatBufferBuilder
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Security
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.time.OffsetDateTime
import java.util.Base64
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.eclipse.paho.client.mqttv3.MqttClient
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class EventServiceTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupBc() {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private lateinit var eventRepository: EventRepository
    private lateinit var eventDeliveryRepository: EventDeliveryRepository
    private lateinit var contractRepository: ContractRepository
    private lateinit var lockSessionService: LockSessionService
    private lateinit var botRepository: BotRepository
    private lateinit var mqttClient: MqttClient
    private lateinit var eventWebSocketHandler: EventWebSocketHandler
    private lateinit var eventService: EventService

    @BeforeEach
    fun setUp() {
        eventRepository = mock(EventRepository::class.java)
        eventDeliveryRepository = mock(EventDeliveryRepository::class.java)
        contractRepository = mock(ContractRepository::class.java)
        lockSessionService = mock(LockSessionService::class.java)
        botRepository = mock(BotRepository::class.java)
        mqttClient = mock(MqttClient::class.java)
        eventWebSocketHandler = mock(EventWebSocketHandler::class.java)

        eventService =
            EventService().apply {
                this.eventRepository = this@EventServiceTest.eventRepository
                this.eventDeliveryRepository = this@EventServiceTest.eventDeliveryRepository
                this.contractRepository = this@EventServiceTest.contractRepository
                this.lockSessionService = this@EventServiceTest.lockSessionService
                this.botRepository = this@EventServiceTest.botRepository
                this.mqttClient = this@EventServiceTest.mqttClient
                this.eventWebSocketHandler = this@EventServiceTest.eventWebSocketHandler
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyObject(): T {
        org.mockito.Mockito.any<T>()
        return null as T
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> capture(captor: ArgumentCaptor<T>): T = captor.capture()

    private fun signEventTable(eventBytes: ByteArray, privateKey: PrivateKey): ByteArray {
        val start = eventBytes[0].toInt() and 0xFF
        val eventStart = eventBytes[start].toInt() and 0xFF
        val vtableStart = start - eventStart
        val payloadToHash = eventBytes.copyOfRange(vtableStart, eventBytes.size)
        val hash = MessageDigest.getInstance("SHA-256").digest(payloadToHash)
        val sig =
            Signature.getInstance("SHA256withECDSA", "BC")
                .apply {
                    initSign(privateKey)
                    update(hash)
                }
                .sign()
        return derToRawSignature(sig)
    }

    private fun buildContractEntityWithBot(
        lockSession: LockSession,
        serialNumber: Int,
        botName: String,
    ): Contract {
        val builder = FlatBufferBuilder(512)
        val botNameOff = builder.createString(botName)
        val botKeyOff = builder.createByteVector(ByteArray(33))
        val permOff = Permission.createPermission(builder, true, true, true, true)
        val botOff = FbBot.createBot(builder, botNameOff, botKeyOff, permOff)
        val botsVec = FbContract.createBotsVector(builder, intArrayOf(botOff))
        val termsOff = builder.createString("Test terms")
        val contractKeyOff = builder.createByteVector(ByteArray(33))
        val contractOff =
            FbContract.createContract(
                builder,
                serialNumber.toUShort(),
                contractKeyOff,
                botsVec,
                termsOff,
                true,
                true,
            )
        val sigOff = builder.createByteVector(ByteArray(64))
        val smOff =
            SignedMessage.createSignedMessage(
                builder,
                sigOff,
                MessagePayload.Contract,
                contractOff,
                0,
            )
        builder.finish(smOff)
        val bodyBytes = builder.sizedByteArray()

        val author =
            AuthorSession(
                publicKey = "test-author-key",
                createdAt = OffsetDateTime.now(),
                updatedAt = OffsetDateTime.now(),
            )
        return Contract(
            state = ContractState.CONFIRMED,
            serialNumber = serialNumber,
            body = bodyBytes,
            shareableToken = "s-123",
            authorSession = author,
            lockSession = lockSession,
            createdAt = OffsetDateTime.now(),
            updatedAt = OffsetDateTime.now(),
        )
    }

    @Test
    fun processIncomingLockEventUsesStandardNameScheme() {
        val keyPair = generateECKeyPair()
        val pubKeyB64 =
            Base64.getEncoder().encodeToString(encodePublicKeySecp1(keyPair.public as ECPublicKey))
        val sessionToken = "ls-test-token"
        val lockSession =
            LockSession(
                publicKey = pubKeyB64,
                sessionToken = sessionToken,
                shareToken = "share-123",
                totalControlToken = "tc-123",
                isLocked = true,
            )

        `when`(lockSessionService.findBySessionToken(sessionToken)).thenReturn(lockSession)

        // Build flatbuffer event
        val builder = FlatBufferBuilder(1024)
        val lockSessionOffset = builder.createString(sessionToken)
        val metadataOffset =
            CommonMetadata.createCommonMetadata(
                builder,
                lockSessionOffset,
                1.toUShort(),
                0.toUShort(),
                1.toUShort(),
                0,
                0,
            )
        Unlock.startUnlock(builder)
        val unlockOffset = Unlock.endUnlock(builder)
        val eventOffset =
            Event.createEvent(builder, metadataOffset, EventType.Unlock, unlockOffset)
        builder.finish(eventOffset)

        val sig = signEventTable(builder.sizedByteArray(), keyPair.private)

        val signedBuilder = FlatBufferBuilder(1024)
        val signedLockSessionOffset = signedBuilder.createString(sessionToken)
        val signedMetadataOffset =
            CommonMetadata.createCommonMetadata(
                signedBuilder,
                signedLockSessionOffset,
                1.toUShort(),
                0.toUShort(),
                1.toUShort(),
                0,
                0,
            )
        Unlock.startUnlock(signedBuilder)
        val signedUnlockOffset = Unlock.endUnlock(signedBuilder)
        val signedEventPayloadOffset =
            Event.createEvent(
                signedBuilder,
                signedMetadataOffset,
                EventType.Unlock,
                signedUnlockOffset,
            )
        val sigOffset = signedBuilder.createByteVector(sig)
        val signedEventOffset =
            SignedEvent.createSignedEvent(signedBuilder, sigOffset, signedEventPayloadOffset)
        signedBuilder.finish(signedEventOffset)
        val payloadBytes = signedBuilder.sizedByteArray()

        val eventCaptor =
            ArgumentCaptor.forClass(club.subjugated.tartarus_coordinator.models.Event::class.java)
        `when`(eventRepository.save(eventCaptor.capture())).thenAnswer { it.arguments[0] }

        val created = eventService.processIncomingLockEvent(sessionToken, payloadBytes)
        assertThat(created).isNotNull
        assertThat(created!!.name).startsWith("ev-")
        assertThat(created.name).doesNotContain(":")
        assertThat(created.eventType).isEqualTo("Unlock")
        assertThat(created.counter).isEqualTo(1)
        assertThat(lockSession.isLocked).isFalse()

        val captured = eventCaptor.value
        assertThat(captured.name).startsWith("ev-")
        assertThat(captured.name).doesNotContain(":")
    }

    @Test
    fun emergencyUnlockUpdatesLockSessionAndPersistsEvent() {
        val keyPair = generateECKeyPair()
        val pubKeyB64 =
            Base64.getEncoder().encodeToString(encodePublicKeySecp1(keyPair.public as ECPublicKey))
        val sessionToken = "ls-emg-token"
        val lockSession =
            LockSession(
                publicKey = pubKeyB64,
                sessionToken = sessionToken,
                shareToken = "share-123",
                totalControlToken = "tc-123",
                isLocked = true,
                emergencyUnlocked = false,
            )

        `when`(lockSessionService.findBySessionToken(sessionToken)).thenReturn(lockSession)
        `when`(eventRepository.save(anyObject())).thenAnswer { it.arguments[0] }

        val builder = FlatBufferBuilder(1024)
        val lockSessionOffset = builder.createString(sessionToken)
        val metadataOffset =
            CommonMetadata.createCommonMetadata(
                builder,
                lockSessionOffset,
                2.toUShort(),
                0.toUShort(),
                3.toUShort(),
                0,
                0,
            )
        EmergencyUnlock.startEmergencyUnlock(builder)
        val emgOffset = EmergencyUnlock.endEmergencyUnlock(builder)
        val eventOffset =
            Event.createEvent(builder, metadataOffset, EventType.EmergencyUnlock, emgOffset)
        builder.finish(eventOffset)

        val sig = signEventTable(builder.sizedByteArray(), keyPair.private)

        val signedBuilder = FlatBufferBuilder(1024)
        val signedLockSessionOffset = signedBuilder.createString(sessionToken)
        val signedMetadataOffset =
            CommonMetadata.createCommonMetadata(
                signedBuilder,
                signedLockSessionOffset,
                2.toUShort(),
                0.toUShort(),
                3.toUShort(),
                0,
                0,
            )
        EmergencyUnlock.startEmergencyUnlock(signedBuilder)
        val signedEmgOffset = EmergencyUnlock.endEmergencyUnlock(signedBuilder)
        val signedEventPayloadOffset =
            Event.createEvent(
                signedBuilder,
                signedMetadataOffset,
                EventType.EmergencyUnlock,
                signedEmgOffset,
            )
        val sigOffset = signedBuilder.createByteVector(sig)
        val signedEventOffset =
            SignedEvent.createSignedEvent(signedBuilder, sigOffset, signedEventPayloadOffset)
        signedBuilder.finish(signedEventOffset)

        val created =
            eventService.processIncomingLockEvent(sessionToken, signedBuilder.sizedByteArray())
        assertThat(created).isNotNull
        assertThat(created!!.eventType).isEqualTo("EmergencyUnlock")
        assertThat(lockSession.isLocked).isFalse()
        assertThat(lockSession.emergencyUnlocked).isTrue()
        verify(lockSessionService).saveLockSession(lockSession)
    }

    @Test
    fun startedUpdateBootstrapsLockSessionAndForwardsOpportunisticallyWithoutDbPersistence() {
        val keyPair = generateECKeyPair()
        val pubKeyBytes = encodePublicKeySecp1(keyPair.public as ECPublicKey)
        val pubKeyB64 = Base64.getEncoder().encodeToString(pubKeyBytes)
        val sessionToken = "ls-started-token"
        val contractSerial = 7

        val lockSession =
            LockSession(
                publicKey = pubKeyB64,
                sessionToken = sessionToken,
                shareToken = "share-123",
                totalControlToken = "tc-123",
                isLocked = false,
            )
        `when`(
                lockSessionService.createLockSession(
                    NewLockSessionMessage(pubKeyB64, sessionToken, "")
                )
            )
            .thenReturn(lockSession)

        val contract = buildContractEntityWithBot(lockSession, contractSerial, "bot-alpha")
        `when`(
                contractRepository.findByLockSessionIdAndSerialNumber(
                    lockSession.id,
                    contractSerial,
                )
            )
            .thenReturn(contract)

        val botEntity =
            Bot(
                name = "bot-alpha",
                state = BotState.ACTIVE,
                publicKey = pubKeyB64,
                description = "Test Bot",
                createdAt = OffsetDateTime.now(),
                updatedAt = OffsetDateTime.now(),
            )
        `when`(botRepository.findByName("bot-alpha")).thenReturn(botEntity)
        `when`(
                eventWebSocketHandler.sendBotMessage(
                    anyString(),
                    nullable(String::class.java),
                    anyString(),
                )
            )
            .thenReturn(true)

        // Build StartedUpdate inside Event
        val builder = FlatBufferBuilder(1024)
        val pubKeyOff = builder.createByteVector(pubKeyBytes)
        val sessionOff = builder.createString(sessionToken)
        val eventIdOff = builder.createString("$sessionToken:$contractSerial:0")
        val metadataOff =
            CommonMetadata.createCommonMetadata(
                builder,
                sessionOff,
                contractSerial.toUShort(),
                0.toUShort(),
                0.toUShort(),
                eventIdOff,
                0,
            )
        val startedUpdateOff =
            StartedUpdate.createStartedUpdate(builder, pubKeyOff, true, true)
        val eventOff =
            Event.createEvent(
                builder,
                metadataOff,
                EventType.StartedUpdate,
                startedUpdateOff,
            )
        builder.finish(eventOff)

        val sig = signEventTable(builder.sizedByteArray(), keyPair.private)

        val signedBuilder = FlatBufferBuilder(1024)
        val sPubKeyOff = signedBuilder.createByteVector(pubKeyBytes)
        val sSessionOff = signedBuilder.createString(sessionToken)
        val sEventIdOff = signedBuilder.createString("$sessionToken:$contractSerial:0")
        val sMetadataOff =
            CommonMetadata.createCommonMetadata(
                signedBuilder,
                sSessionOff,
                contractSerial.toUShort(),
                0.toUShort(),
                0.toUShort(),
                sEventIdOff,
                0,
            )
        val sStartedUpdateOff =
            StartedUpdate.createStartedUpdate(signedBuilder, sPubKeyOff, true, true)
        val sEventOff =
            Event.createEvent(
                signedBuilder,
                sMetadataOff,
                EventType.StartedUpdate,
                sStartedUpdateOff,
            )
        val sigOff = signedBuilder.createByteVector(sig)
        val signedEventOff = SignedEvent.createSignedEvent(signedBuilder, sigOff, sEventOff)
        signedBuilder.finish(signedEventOff)

        val result =
            eventService.processIncomingLockEvent(sessionToken, signedBuilder.sizedByteArray())

        // Ephemeral: returns null and does NOT save to EventRepository or EventDeliveryRepository
        assertThat(result).isNull()
        assertThat(lockSession.isLocked).isTrue()
        verify(eventRepository, never()).save(anyObject())
        verify(eventDeliveryRepository, never()).save(anyObject())

        // Opportunistically forwarded over WebSocket to active bot
        val botNameCaptor = ArgumentCaptor.forClass(String::class.java)
        val wsCaptor = ArgumentCaptor.forClass(String::class.java)
        verify(eventWebSocketHandler)
            .sendBotMessage(
                botNameCaptor.capture() ?: "",
                nullable(String::class.java),
                wsCaptor.capture() ?: "",
            )
        assertThat(botNameCaptor.value).isEqualTo("bot-alpha")
        assertThat(wsCaptor.value).contains("\"eventType\":\"StartedUpdate\"")
    }

    @Test
    fun periodicUpdateUpdatesLockSessionWithDeviceUptimeAndForwardsOpportunisticallyWithoutDbPersistence() {
        val keyPair = generateECKeyPair()
        val pubKeyBytes = encodePublicKeySecp1(keyPair.public as ECPublicKey)
        val pubKeyB64 = Base64.getEncoder().encodeToString(pubKeyBytes)
        val sessionToken = "ls-periodic-token"
        val contractSerial = 9
        val uptimeSeconds = 86400UL

        val lockSession =
            LockSession(
                publicKey = pubKeyB64,
                sessionToken = sessionToken,
                shareToken = "share-123",
                totalControlToken = "tc-123",
            )
        `when`(lockSessionService.findBySessionToken(sessionToken)).thenReturn(lockSession)

        val contract = buildContractEntityWithBot(lockSession, contractSerial, "bot-beta")
        `when`(
                contractRepository.findByLockSessionIdAndSerialNumber(
                    lockSession.id,
                    contractSerial,
                )
            )
            .thenReturn(contract)

        val botEntity =
            Bot(
                name = "bot-beta",
                state = BotState.ACTIVE,
                publicKey = pubKeyB64,
                description = "Test Bot",
                createdAt = OffsetDateTime.now(),
                updatedAt = OffsetDateTime.now(),
            )
        `when`(botRepository.findByName("bot-beta")).thenReturn(botEntity)
        `when`(
                eventWebSocketHandler.sendBotMessage(
                    anyString(),
                    nullable(String::class.java),
                    anyString(),
                )
            )
            .thenReturn(true)

        // Build PeriodicUpdate inside Event
        val builder = FlatBufferBuilder(1024)
        val sessionOff = builder.createString(sessionToken)
        val eventIdOff = builder.createString("$sessionToken:$contractSerial:0")
        val metadataOff =
            CommonMetadata.createCommonMetadata(
                builder,
                sessionOff,
                contractSerial.toUShort(),
                0.toUShort(),
                0.toUShort(),
                eventIdOff,
                0,
            )
        val periodicOff =
            PeriodicUpdate.createPeriodicUpdate(builder, true, false, uptimeSeconds)
        val eventOff =
            Event.createEvent(
                builder,
                metadataOff,
                EventType.PeriodicUpdate,
                periodicOff,
            )
        builder.finish(eventOff)

        val sig = signEventTable(builder.sizedByteArray(), keyPair.private)

        val signedBuilder = FlatBufferBuilder(1024)
        val sSessionOff = signedBuilder.createString(sessionToken)
        val sEventIdOff = signedBuilder.createString("$sessionToken:$contractSerial:0")
        val sMetadataOff =
            CommonMetadata.createCommonMetadata(
                signedBuilder,
                sSessionOff,
                contractSerial.toUShort(),
                0.toUShort(),
                0.toUShort(),
                sEventIdOff,
                0,
            )
        val sPeriodicOff =
            PeriodicUpdate.createPeriodicUpdate(signedBuilder, true, false, uptimeSeconds)
        val sEventOff =
            Event.createEvent(
                signedBuilder,
                sMetadataOff,
                EventType.PeriodicUpdate,
                sPeriodicOff,
            )
        val sigOff = signedBuilder.createByteVector(sig)
        val signedEventOff = SignedEvent.createSignedEvent(signedBuilder, sigOff, sEventOff)
        signedBuilder.finish(signedEventOff)

        val result =
            eventService.processIncomingLockEvent(sessionToken, signedBuilder.sizedByteArray())

        // Ephemeral: returns null and does NOT save to EventRepository or EventDeliveryRepository
        assertThat(result).isNull()
        verify(eventRepository, never()).save(anyObject())
        verify(eventDeliveryRepository, never()).save(anyObject())

        val sessionCaptor = ArgumentCaptor.forClass(String::class.java)
        val updateCaptor = ArgumentCaptor.forClass(PeriodicUpdate::class.java)
        verify(lockSessionService)
            .handlePeriodicUpdate(
                sessionCaptor.capture() ?: "",
                anyInt(),
                updateCaptor.capture() ?: PeriodicUpdate(),
            )
        assertThat(sessionCaptor.value).isEqualTo(sessionToken)
        assertThat(updateCaptor.value.isLocked).isTrue()
        assertThat(updateCaptor.value.emergencyUnlocked).isFalse()
        assertThat(updateCaptor.value.deviceUptime).isEqualTo(uptimeSeconds)

        // Opportunistically forwarded over WebSocket with deviceUptime
        val botNameCaptor = ArgumentCaptor.forClass(String::class.java)
        val wsCaptor = ArgumentCaptor.forClass(String::class.java)
        verify(eventWebSocketHandler)
            .sendBotMessage(
                botNameCaptor.capture() ?: "",
                nullable(String::class.java),
                wsCaptor.capture() ?: "",
            )
        assertThat(botNameCaptor.value).isEqualTo("bot-beta")
        assertThat(wsCaptor.value).contains("\"eventType\":\"PeriodicUpdate\"")
        assertThat(wsCaptor.value).contains("\"deviceUptime\":86400")
    }
}
