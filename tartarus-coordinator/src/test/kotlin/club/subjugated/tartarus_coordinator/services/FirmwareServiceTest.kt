package club.subjugated.tartarus_coordinator.services

import club.subjugated.fb.message.firmware.FirmwareChallengeRequest
import club.subjugated.fb.message.firmware.FirmwareMessage
import club.subjugated.tartarus_coordinator.events.FirmwareValidationEvent
import club.subjugated.tartarus_coordinator.storage.FirmwareRepository
import club.subjugated.tartarus_coordinator.storage.FirmwareUpgradePathRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import club.subjugated.tartarus_coordinator.util.derToRawSignature
import club.subjugated.tartarus_coordinator.util.encodePublicKeySecp1
import club.subjugated.tartarus_coordinator.util.generateECKeyPair
import java.nio.ByteBuffer
import java.security.Security
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.context.ApplicationEventPublisher

class FirmwareServiceTest {

    private lateinit var firmwareRepository: FirmwareRepository
    private lateinit var firmwareUpgradePathRepository: FirmwareUpgradePathRepository
    private lateinit var timeSource: TimeSource
    private lateinit var eventPublisher: ApplicationEventPublisher
    private lateinit var firmwareService: FirmwareService

    @BeforeEach
    fun setUp() {
        firmwareRepository = mock(FirmwareRepository::class.java)
        firmwareUpgradePathRepository = mock(FirmwareUpgradePathRepository::class.java)
        timeSource = mock(TimeSource::class.java)
        eventPublisher = mock(ApplicationEventPublisher::class.java)

        firmwareService =
            FirmwareService(
                firmwareRepository,
                firmwareUpgradePathRepository,
                timeSource,
                eventPublisher,
            )
    }

    @Test
    fun testGenerateAndValidateChallenge_Success() {
        val keyPair = generateECKeyPair()
        val pubKeyBase64 =
            Base64.getEncoder().encodeToString(encodePublicKeySecp1(keyPair.public as ECPublicKey))
        firmwareService.challengeKey = pubKeyBase64

        val challengeBytes = firmwareService.generateFirmwareChallenge("session-token-1")
        assertThat(challengeBytes).isNotEmpty()

        val firmwareMessage =
            FirmwareMessage.getRootAsFirmwareMessage(ByteBuffer.wrap(challengeBytes))
        val requestId = firmwareMessage.requestId
        val challengeRequest =
            firmwareMessage.payload(FirmwareChallengeRequest()) as FirmwareChallengeRequest
        val nonce = ByteArray(challengeRequest.nonceLength)
        for (i in 0 until challengeRequest.nonceLength) {
            nonce[i] = challengeRequest.nonce(i).toByte()
        }

        val signer =
            Signature.getInstance("NonewithECDSA", "BC").apply {
                initSign(keyPair.private)
                update(nonce)
            }
        val derSig = signer.sign()
        val rawSig = derToRawSignature(derSig)

        val isValid =
            firmwareService.validateChallengeSignature(requestId, "session-token-1", rawSig)
        assertThat(isValid).isTrue()

        val eventCaptor = ArgumentCaptor.forClass(FirmwareValidationEvent::class.java)
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        assertThat(eventCaptor.value.sessionToken).isEqualTo("session-token-1")
        assertThat(eventCaptor.value.validated).isTrue()
    }

    @Test
    fun testValidateChallenge_ReplayPrevention() {
        val keyPair = generateECKeyPair()
        val pubKeyBase64 =
            Base64.getEncoder().encodeToString(encodePublicKeySecp1(keyPair.public as ECPublicKey))
        firmwareService.challengeKey = pubKeyBase64

        val challengeBytes = firmwareService.generateFirmwareChallenge("session-token-replay")
        val firmwareMessage =
            FirmwareMessage.getRootAsFirmwareMessage(ByteBuffer.wrap(challengeBytes))
        val requestId = firmwareMessage.requestId
        val challengeRequest =
            firmwareMessage.payload(FirmwareChallengeRequest()) as FirmwareChallengeRequest
        val nonce = ByteArray(challengeRequest.nonceLength)
        for (i in 0 until challengeRequest.nonceLength) {
            nonce[i] = challengeRequest.nonce(i).toByte()
        }

        val signer =
            Signature.getInstance("NonewithECDSA", "BC").apply {
                initSign(keyPair.private)
                update(nonce)
            }
        val rawSig = derToRawSignature(signer.sign())

        // First attempt succeeds
        assertThat(
                firmwareService.validateChallengeSignature(
                    requestId,
                    "session-token-replay",
                    rawSig,
                )
            )
            .isTrue()

        // Second attempt with same requestId fails because nonce was invalidated
        val isSecondValid =
            firmwareService.validateChallengeSignature(requestId, "session-token-replay", rawSig)
        assertThat(isSecondValid).isFalse()
    }

    @Test
    fun testValidateChallenge_WrongKeyFails() {
        val keyPair = generateECKeyPair()
        val wrongKeyPair = generateECKeyPair()
        val pubKeyBase64 =
            Base64.getEncoder().encodeToString(encodePublicKeySecp1(keyPair.public as ECPublicKey))
        firmwareService.challengeKey = pubKeyBase64

        val challengeBytes = firmwareService.generateFirmwareChallenge("session-token-wrong")
        val firmwareMessage =
            FirmwareMessage.getRootAsFirmwareMessage(ByteBuffer.wrap(challengeBytes))
        val requestId = firmwareMessage.requestId
        val challengeRequest =
            firmwareMessage.payload(FirmwareChallengeRequest()) as FirmwareChallengeRequest
        val nonce = ByteArray(challengeRequest.nonceLength)
        for (i in 0 until challengeRequest.nonceLength) {
            nonce[i] = challengeRequest.nonce(i).toByte()
        }

        // Sign with the wrong private key
        val signer =
            Signature.getInstance("NonewithECDSA", "BC").apply {
                initSign(wrongKeyPair.private)
                update(nonce)
            }
        val rawSig = derToRawSignature(signer.sign())

        val isValid =
            firmwareService.validateChallengeSignature(requestId, "session-token-wrong", rawSig)
        assertThat(isValid).isFalse()

        val eventCaptor = ArgumentCaptor.forClass(FirmwareValidationEvent::class.java)
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        assertThat(eventCaptor.value.validated).isFalse()
    }

    @Test
    fun testValidateChallenge_UnknownRequestIdFailsSafely() {
        val keyPair = generateECKeyPair()
        firmwareService.challengeKey =
            Base64.getEncoder().encodeToString(encodePublicKeySecp1(keyPair.public as ECPublicKey))

        // Non-existent requestId (nonce not found in cache)
        val isValid =
            firmwareService.validateChallengeSignature(999999L, "unknown-session", ByteArray(64))
        assertThat(isValid).isFalse()

        val eventCaptor = ArgumentCaptor.forClass(FirmwareValidationEvent::class.java)
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        assertThat(eventCaptor.value.sessionToken).isEqualTo("unknown-session")
        assertThat(eventCaptor.value.validated).isFalse()
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun installBouncyCastle() {
            Security.addProvider(BouncyCastleProvider())
        }
    }
}
