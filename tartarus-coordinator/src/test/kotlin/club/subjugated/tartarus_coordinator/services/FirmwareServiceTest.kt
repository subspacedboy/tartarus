package club.subjugated.tartarus_coordinator.services

import club.subjugated.fb.message.firmware.FirmwareChallengeRequest
import club.subjugated.fb.message.firmware.FirmwareMessage
import club.subjugated.tartarus_coordinator.events.FirmwareValidationEvent
import club.subjugated.tartarus_coordinator.models.ProvisionedHardwareUnit
import club.subjugated.tartarus_coordinator.storage.FirmwareRepository
import club.subjugated.tartarus_coordinator.storage.FirmwareUpgradePathRepository
import club.subjugated.tartarus_coordinator.storage.HardwareAttestationLogRepository
import club.subjugated.tartarus_coordinator.storage.ProvisionedHardwareUnitRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import club.subjugated.tartarus_coordinator.util.derToRawSignature
import club.subjugated.tartarus_coordinator.util.encodePublicKeySecp1
import club.subjugated.tartarus_coordinator.util.generateECKeyPair
import java.nio.ByteBuffer
import java.security.Security
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.time.OffsetDateTime
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher

class FirmwareServiceTest {

    private lateinit var firmwareRepository: FirmwareRepository
    private lateinit var firmwareUpgradePathRepository: FirmwareUpgradePathRepository
    private lateinit var unitRepository: ProvisionedHardwareUnitRepository
    private lateinit var logRepository: HardwareAttestationLogRepository
    private lateinit var timeSource: TimeSource
    private lateinit var eventPublisher: ApplicationEventPublisher
    private lateinit var firmwareService: FirmwareService

    @BeforeEach
    fun setUp() {
        firmwareRepository = mock(FirmwareRepository::class.java)
        firmwareUpgradePathRepository = mock(FirmwareUpgradePathRepository::class.java)
        unitRepository = mock(ProvisionedHardwareUnitRepository::class.java)
        logRepository = mock(HardwareAttestationLogRepository::class.java)
        timeSource = mock(TimeSource::class.java)
        `when`(timeSource.nowInUtc()).thenReturn(OffsetDateTime.parse("2026-10-22T12:00:00Z"))
        eventPublisher = mock(ApplicationEventPublisher::class.java)

        firmwareService =
            FirmwareService(
                firmwareRepository,
                firmwareUpgradePathRepository,
                timeSource,
                eventPublisher,
                unitRepository,
                logRepository,
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

    @Test
    fun testValidateChallenge_PassesCurrentFirmware() {
        val keyPair = generateECKeyPair()
        val pubKeyBase64 =
            Base64.getEncoder().encodeToString(encodePublicKeySecp1(keyPair.public as ECPublicKey))
        firmwareService.challengeKey = pubKeyBase64

        val challengeBytes = firmwareService.generateFirmwareChallenge("session-token-version")
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
            firmwareService.validateChallengeSignature(
                requestId,
                "session-token-version",
                rawSig,
                currentFirmware = "0.1.2",
            )
        assertThat(isValid).isTrue()

        val eventCaptor = ArgumentCaptor.forClass(FirmwareValidationEvent::class.java)
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        assertThat(eventCaptor.value.sessionToken).isEqualTo("session-token-version")
        assertThat(eventCaptor.value.validated).isTrue()
        assertThat(eventCaptor.value.currentFirmware).isEqualTo("0.1.2")
    }

    private fun getUncompressedSec1(publicKey: ECPublicKey): ByteArray {
        val w = publicKey.w
        val xBytes = w.affineX.toByteArray()
        val yBytes = w.affineY.toByteArray()
        val x32 = ByteArray(32)
        val y32 = ByteArray(32)
        val xStart = if (xBytes.size > 32) 1 else 0
        val xLen = xBytes.size - xStart
        System.arraycopy(xBytes, xStart, x32, 32 - xLen, xLen)
        val yStart = if (yBytes.size > 32) 1 else 0
        val yLen = yBytes.size - yStart
        System.arraycopy(yBytes, yStart, y32, 32 - yLen, yLen)
        return byteArrayOf(0x04) + x32 + y32
    }

    @Test
    fun testValidateChallenge_WithRegisteredHardwareId_Success() {
        val authKey = generateECKeyPair()
        val authSec1 = getUncompressedSec1(authKey.public as ECPublicKey)
        val hardwareId = "cd16338201001c00010000000a1b2c3d4e5f60718293a4b5c6d7e8"

        val unit =
            ProvisionedHardwareUnit(
                hardwareId = hardwareId,
                provisioningMode = "HARD",
                authPubkeyB64 = Base64.getEncoder().encodeToString(authSec1),
                manufacturerSigB64 = "sig",
                attestationDigestHex = "digest",
                infineonCertsJson = "[]",
                leafCertSubject = "leaf",
                leafCertIssuer = "issuer",
                leafCertSerialHex = "123",
                initialSessionToken = "ORIG12",
            )

        `when`(unitRepository.findByHardwareId(hardwareId)).thenReturn(unit)

        val challengeBytes = firmwareService.generateFirmwareChallenge("ROTATED34")
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
            Signature.getInstance("NONEwithECDSA", "BC").apply {
                initSign(authKey.private)
                update(nonce)
            }
        val rawSig = derToRawSignature(signer.sign())

        val isValid =
            firmwareService.validateChallengeSignature(
                requestId,
                "ROTATED34",
                rawSig,
                currentFirmware = "1.0.0",
                hardwareId = hardwareId,
            )

        assertThat(isValid).isTrue()
        assertThat(unit.lastKnownSessionToken).isEqualTo("ROTATED34")
        assertThat(unit.lastFirmwareVersion).isEqualTo("1.0.0")
        verify(unitRepository).save(unit)
    }

    @Test
    fun testValidateChallenge_WithUnregisteredHardwareId_Fails() {
        val hardwareId = "000000000000000000000000000000000000000000000000000000"
        `when`(unitRepository.findByHardwareId(hardwareId)).thenReturn(null)

        val challengeBytes = firmwareService.generateFirmwareChallenge("SES123")
        val firmwareMessage =
            FirmwareMessage.getRootAsFirmwareMessage(ByteBuffer.wrap(challengeBytes))
        val requestId = firmwareMessage.requestId

        val isValid =
            firmwareService.validateChallengeSignature(
                requestId,
                "SES123",
                ByteArray(64),
                currentFirmware = "1.0.0",
                hardwareId = hardwareId,
            )

        assertThat(isValid).isFalse()
    }

    @Test
    fun testValidateChallenge_WithRevokedHardwareId_Fails() {
        val hardwareId = "cd16338201001c00010000000a1b2c3d4e5f60718293a4b5c6d7e8"
        val unit =
            ProvisionedHardwareUnit(
                hardwareId = hardwareId,
                provisioningMode = "HARD",
                authPubkeyB64 = "pubkey",
                manufacturerSigB64 = "sig",
                attestationDigestHex = "digest",
                infineonCertsJson = "[]",
                leafCertSubject = "leaf",
                leafCertIssuer = "issuer",
                leafCertSerialHex = "123",
                initialSessionToken = "ORIG12",
                isRevoked = true,
            )
        `when`(unitRepository.findByHardwareId(hardwareId)).thenReturn(unit)

        val challengeBytes = firmwareService.generateFirmwareChallenge("SES123")
        val firmwareMessage =
            FirmwareMessage.getRootAsFirmwareMessage(ByteBuffer.wrap(challengeBytes))
        val requestId = firmwareMessage.requestId

        val isValid =
            firmwareService.validateChallengeSignature(
                requestId,
                "SES123",
                ByteArray(64),
                currentFirmware = "1.0.0",
                hardwareId = hardwareId,
            )

        assertThat(isValid).isFalse()
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun installBouncyCastle() {
            Security.addProvider(BouncyCastleProvider())
        }
    }
}
