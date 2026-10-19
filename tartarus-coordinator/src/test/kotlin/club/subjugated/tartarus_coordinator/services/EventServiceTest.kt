package club.subjugated.tartarus_coordinator.services

import club.subjugated.fb.event.CommonMetadata
import club.subjugated.fb.event.Event
import club.subjugated.fb.event.EventType
import club.subjugated.fb.event.SignedEvent
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
import java.security.Security
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.util.Base64
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.eclipse.paho.client.mqttv3.MqttClient
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

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
    private lateinit var eventService: EventService

    @BeforeEach
    fun setUp() {
        eventRepository = mock(EventRepository::class.java)
        eventDeliveryRepository = mock(EventDeliveryRepository::class.java)
        contractRepository = mock(ContractRepository::class.java)
        lockSessionService = mock(LockSessionService::class.java)
        botRepository = mock(BotRepository::class.java)
        mqttClient = mock(MqttClient::class.java)

        eventService = EventService().apply {
            this.eventRepository = this@EventServiceTest.eventRepository
            this.eventDeliveryRepository = this@EventServiceTest.eventDeliveryRepository
            this.contractRepository = this@EventServiceTest.contractRepository
            this.lockSessionService = this@EventServiceTest.lockSessionService
            this.botRepository = this@EventServiceTest.botRepository
            this.mqttClient = this@EventServiceTest.mqttClient
        }
    }

    @Test
    fun processIncomingLockEventUsesStandardNameScheme() {
        val keyPair = generateECKeyPair()
        val pubKeyB64 = Base64.getEncoder().encodeToString(encodePublicKeySecp1(keyPair.public as ECPublicKey))
        val sessionToken = "ls-test-token"
        val lockSession = LockSession(
            publicKey = pubKeyB64,
            sessionToken = sessionToken,
            shareToken = "share-123",
            totalControlToken = "tc-123",
        )

        `when`(lockSessionService.findBySessionToken(sessionToken)).thenReturn(lockSession)

        // Build flatbuffer event
        val builder = FlatBufferBuilder(1024)
        val lockSessionOffset = builder.createString(sessionToken)
        val metadataOffset = CommonMetadata.createCommonMetadata(
            builder,
            lockSessionOffset,
            1.toUShort(),
            0.toUShort(),
            1.toUShort(),
            0,
            0,
        )
        val eventOffset = Event.createEvent(builder, metadataOffset, EventType.Unlock)
        builder.finish(eventOffset)

        val eventBytes = builder.sizedByteArray()
        val start = eventBytes[0].toInt() and 0xFF
        val eventStart = eventBytes[start].toInt() and 0xFF
        val vtableStart = start - eventStart
        val payloadToHash = eventBytes.copyOfRange(vtableStart, eventBytes.size)
        val hash = MessageDigest.getInstance("SHA-256").digest(payloadToHash)
        var sig = Signature.getInstance("SHA256withECDSA", "BC").apply {
            initSign(keyPair.private)
            update(hash)
        }.sign()
        sig = derToRawSignature(sig)

        val signedBuilder = FlatBufferBuilder(1024)
        val signedLockSessionOffset = signedBuilder.createString(sessionToken)
        val signedMetadataOffset = CommonMetadata.createCommonMetadata(
            signedBuilder,
            signedLockSessionOffset,
            1.toUShort(),
            0.toUShort(),
            1.toUShort(),
            0,
            0,
        )
        val signedEventPayloadOffset = Event.createEvent(signedBuilder, signedMetadataOffset, EventType.Unlock)
        val sigOffset = signedBuilder.createByteVector(sig)
        val signedEventOffset = SignedEvent.createSignedEvent(signedBuilder, sigOffset, signedEventPayloadOffset)
        signedBuilder.finish(signedEventOffset)
        val payloadBytes = signedBuilder.sizedByteArray()

        val eventCaptor = ArgumentCaptor.forClass(club.subjugated.tartarus_coordinator.models.Event::class.java)
        `when`(eventRepository.save(eventCaptor.capture())).thenAnswer { it.arguments[0] }

        val created = eventService.processIncomingLockEvent(sessionToken, payloadBytes)
        assertThat(created).isNotNull
        assertThat(created!!.name).startsWith("ev-")
        assertThat(created.name).doesNotContain(":")
        assertThat(created.eventType).isEqualTo("Unlock")
        assertThat(created.counter).isEqualTo(1)

        val captured = eventCaptor.value
        assertThat(captured.name).startsWith("ev-")
        assertThat(captured.name).doesNotContain(":")
    }
}
