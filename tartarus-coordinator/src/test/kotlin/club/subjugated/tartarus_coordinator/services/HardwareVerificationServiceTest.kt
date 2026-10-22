package club.subjugated.tartarus_coordinator.services

import club.subjugated.tartarus_coordinator.api.messages.EfuseStatusPayload
import club.subjugated.tartarus_coordinator.api.messages.ProvisioningBlob
import club.subjugated.tartarus_coordinator.api.messages.ProvisioningIngestRequest
import club.subjugated.tartarus_coordinator.api.messages.UpdateHardwareUnitRequest
import club.subjugated.tartarus_coordinator.models.LockSession
import club.subjugated.tartarus_coordinator.models.ProvisionedHardwareUnit
import club.subjugated.tartarus_coordinator.storage.HardwareAttestationLogRepository
import club.subjugated.tartarus_coordinator.storage.LockSessionRepository
import club.subjugated.tartarus_coordinator.storage.ProvisionedHardwareUnitRepository
import club.subjugated.tartarus_coordinator.util.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigInteger
import java.security.*
import java.security.interfaces.ECPublicKey
import java.time.OffsetDateTime
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.springframework.web.server.ResponseStatusException

class HardwareVerificationServiceTest {

    private lateinit var unitRepository: ProvisionedHardwareUnitRepository
    private lateinit var logRepository: HardwareAttestationLogRepository
    private lateinit var lockSessionRepository: LockSessionRepository
    private lateinit var timeSource: TimeSource
    private lateinit var service: HardwareVerificationService

    companion object {
        init {
            if (Security.getProvider("BC") == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }

    @BeforeEach
    fun setUp() {
        unitRepository = mock(ProvisionedHardwareUnitRepository::class.java)
        logRepository = mock(HardwareAttestationLogRepository::class.java)
        lockSessionRepository = mock(LockSessionRepository::class.java)
        timeSource = mock(TimeSource::class.java)
        `when`(timeSource.nowInUtc()).thenReturn(OffsetDateTime.parse("2026-10-22T12:00:00Z"))

        service =
            HardwareVerificationService(
                unitRepository,
                logRepository,
                timeSource,
                lockSessionRepository,
            )
        service.loadBundledCertificates()
    }

    private fun generateCert(
        keyPair: KeyPair,
        subjectDn: String,
        issuerDn: String,
        issuerPrivateKey: PrivateKey,
    ): ByteArray {
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 1000L * 60)
        val notAfter = Date(now + 1000L * 60 * 60 * 24 * 365)
        val serialNumber = BigInteger.valueOf(System.nanoTime())

        val certBuilder =
            JcaX509v3CertificateBuilder(
                X500Name(issuerDn),
                serialNumber,
                notBefore,
                notAfter,
                X500Name(subjectDn),
                keyPair.public,
            )

        val signer =
            JcaContentSignerBuilder("SHA256withECDSA").setProvider("BC").build(issuerPrivateKey)

        val certHolder = certBuilder.build(signer)
        return JcaX509CertificateConverter().setProvider("BC").getCertificate(certHolder).encoded
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
    fun testEndToEndVerification_SoftProvisioned() {
        // 1. Generate Infineon Factory Keypair (0xE0F0) and self-signed or bundled root
        val rootKey = generateECKeyPair()
        val rootCertDer =
            generateCert(
                rootKey,
                "CN=Infineon OPTIGA(TM) Trust M Test CA 000, O=Infineon, C=DE",
                "CN=Infineon OPTIGA(TM) Trust M Test CA 000, O=Infineon, C=DE",
                rootKey.private,
            )

        val factoryKey = generateECKeyPair()
        val leafCertDer =
            generateCert(
                factoryKey,
                "CN=OPTIGA(TM) Trust M 0000000001, O=Infineon, C=DE",
                "CN=Infineon OPTIGA(TM) Trust M Test CA 000, O=Infineon, C=DE",
                rootKey.private,
            )

        // 2. Generate Authenticity Keypair (0xE0F2)
        val authKey = generateECKeyPair()
        val authSec1 = getUncompressedSec1(authKey.public as ECPublicKey)
        val coprUidHex = "cd16338201001c00010000000a1b2c3d4e5f60718293a4b5c6d7e8"
        val coprUidBytes = HexFormat.of().parseHex(coprUidHex)

        // 3. Compute attestation digest
        val domain = "TARTARUS-AUTH-KEY-V1:".toByteArray(Charsets.US_ASCII)
        val preimage = domain + coprUidBytes + authSec1
        val digest = MessageDigest.getInstance("SHA-256").digest(preimage)
        val digestHex = HexFormat.of().formatHex(digest)

        // 4. Sign digest with factory private key (0xE0F0)
        val dsaSigner = Signature.getInstance("NONEwithECDSA", "BC")
        dsaSigner.initSign(factoryKey.private)
        dsaSigner.update(digest)
        val derSig = dsaSigner.sign()
        val p1363Sig = derToRawSignature(derSig, 64)

        val blob =
            ProvisioningBlob(
                status = "SOFT_PROVISIONED",
                sessionToken = "ABC234",
                coprUidHex = coprUidHex,
                lockPubkeyB64 = Base64.getEncoder().encodeToString(authSec1),
                authPubkeyB64 = Base64.getEncoder().encodeToString(authSec1),
                manufacturerSigB64 = Base64.getEncoder().encodeToString(p1363Sig),
                attestationDigestHex = digestHex,
                infineonCertsDerB64 =
                    listOf(
                        Base64.getEncoder().encodeToString(leafCertDer),
                        Base64.getEncoder().encodeToString(rootCertDer),
                    ),
                efuseStatus =
                    EfuseStatusPayload(
                        blk3Provisioned = false,
                        blk3WriteProtected = false,
                        secureBootEnabled = false,
                    ),
            )

        val json = jacksonObjectMapper().writeValueAsString(blob)
        val rawLine = "SOFT_PROVISION_RESULT:$json"

        val request =
            ProvisioningIngestRequest(
                rawPayload = rawLine,
                label = "Bench dev unit",
                notes = "Desk test board",
            )

        // Test Preview
        val preview = service.verifyPreview(request)
        assertThat(preview.valid).isTrue()
        assertThat(preview.provisioningMode).isEqualTo("SOFT")
        assertThat(preview.hardwareId).isEqualTo(coprUidHex)
        assertThat(preview.chainValid).isTrue()
        assertThat(preview.attestationDigestValid).isTrue()
        assertThat(preview.signatureValid).isTrue()
        assertThat(preview.errors).isEmpty()

        // Test Save / Ingest
        `when`(unitRepository.save(any(ProvisionedHardwareUnit::class.java))).thenAnswer {
            it.arguments[0]
        }
        val detail = service.ingestAndSave(request)

        assertThat(detail.hardwareId).isEqualTo(coprUidHex)
        assertThat(detail.provisioningMode).isEqualTo("SOFT")
        assertThat(detail.label).isEqualTo("Bench dev unit")
        assertThat(detail.notes).isEqualTo("Desk test board")
        assertThat(detail.initialSessionToken).isEqualTo("ABC234")
        verify(unitRepository).save(any(ProvisionedHardwareUnit::class.java))
    }

    @Test
    fun testDowngradePrevention_HardToSoftThrows() {
        val existingHardUnit =
            ProvisionedHardwareUnit(
                hardwareId = "cd16338201001c00010000000a1b2c3d4e5f60718293a4b5c6d7e8",
                provisioningMode = "HARD",
                authPubkeyB64 = "pubkey",
                manufacturerSigB64 = "sig",
                attestationDigestHex = "digest",
                infineonCertsJson = "[]",
                leafCertSubject = "leaf",
                leafCertIssuer = "issuer",
                leafCertSerialHex = "123",
                initialSessionToken = "ABC234",
            )

        `when`(
                unitRepository.findByHardwareId(
                    "cd16338201001c00010000000a1b2c3d4e5f60718293a4b5c6d7e8"
                )
            )
            .thenReturn(existingHardUnit)

        // Mock verification steps to pass
        val rootKey = generateECKeyPair()
        val rootCertDer =
            generateCert(
                rootKey,
                "CN=Infineon OPTIGA(TM) Trust M Test CA 000, O=Infineon, C=DE",
                "CN=Infineon OPTIGA(TM) Trust M Test CA 000, O=Infineon, C=DE",
                rootKey.private,
            )
        val factoryKey = generateECKeyPair()
        val leafCertDer =
            generateCert(
                factoryKey,
                "CN=OPTIGA(TM) Trust M 0000000001, O=Infineon, C=DE",
                "CN=Infineon OPTIGA(TM) Trust M Test CA 000, O=Infineon, C=DE",
                rootKey.private,
            )
        val authKey = generateECKeyPair()
        val authSec1 = getUncompressedSec1(authKey.public as ECPublicKey)
        val coprUidHex = "cd16338201001c00010000000a1b2c3d4e5f60718293a4b5c6d7e8"
        val coprUidBytes = HexFormat.of().parseHex(coprUidHex)
        val preimage =
            "TARTARUS-AUTH-KEY-V1:".toByteArray(Charsets.US_ASCII) + coprUidBytes + authSec1
        val digest = MessageDigest.getInstance("SHA-256").digest(preimage)
        val digestHex = HexFormat.of().formatHex(digest)

        val dsaSigner = Signature.getInstance("NONEwithECDSA", "BC")
        dsaSigner.initSign(factoryKey.private)
        dsaSigner.update(digest)
        val p1363Sig = derToRawSignature(dsaSigner.sign(), 64)

        val blob =
            ProvisioningBlob(
                status = "SOFT_PROVISIONED",
                sessionToken = "ABC234",
                coprUidHex = coprUidHex,
                authPubkeyB64 = Base64.getEncoder().encodeToString(authSec1),
                manufacturerSigB64 = Base64.getEncoder().encodeToString(p1363Sig),
                attestationDigestHex = digestHex,
                infineonCertsDerB64 =
                    listOf(
                        Base64.getEncoder().encodeToString(leafCertDer),
                        Base64.getEncoder().encodeToString(rootCertDer),
                    ),
                efuseStatus = EfuseStatusPayload(blk3Provisioned = false),
            )

        val request =
            ProvisioningIngestRequest(rawPayload = jacksonObjectMapper().writeValueAsString(blob))

        assertThatThrownBy { service.ingestAndSave(request) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("Cannot downgrade a HARD provisioned unit to SOFT")
    }

    @Test
    fun testUpdateUnit_RevokeAndNotes() {
        val existing =
            ProvisionedHardwareUnit(
                hardwareId = "cd16338201001c00010000000a1b2c3d4e5f60718293a4b5c6d7e8",
                provisioningMode = "HARD",
                authPubkeyB64 = "pubkey",
                manufacturerSigB64 = "sig",
                attestationDigestHex = "digest",
                infineonCertsJson = "[]",
                leafCertSubject = "leaf",
                leafCertIssuer = "issuer",
                leafCertSerialHex = "123",
                initialSessionToken = "ABC234",
                isRevoked = false,
            )

        `when`(
                unitRepository.findByHardwareId(
                    "cd16338201001c00010000000a1b2c3d4e5f60718293a4b5c6d7e8"
                )
            )
            .thenReturn(existing)
        `when`(unitRepository.save(any(ProvisionedHardwareUnit::class.java))).thenAnswer {
            it.arguments[0]
        }

        val updated =
            service.updateUnit(
                "cd16338201001c00010000000a1b2c3d4e5f60718293a4b5c6d7e8",
                UpdateHardwareUnitRequest(
                    label = "Unit 42",
                    notes = "Stolen from lab",
                    isRevoked = true,
                    revokedReason = "Device reported lost",
                ),
            )

        assertThat(updated.isRevoked).isTrue()
        assertThat(updated.revokedReason).isEqualTo("Device reported lost")
        assertThat(updated.label).isEqualTo("Unit 42")
        assertThat(updated.notes).isEqualTo("Stolen from lab")
    }

    @Test
    fun testRealDeviceBlob_VerificationSuccess() {
        val rawPayload =
            """SOFT_PROVISION_RESULT:{"status":"SOFT_PROVISIONED","session_token":"VLEPLJ","copr_uid_hex":"cd16338401001c000100000a091b5c000b00820068801010710809","lock_pubkey_b64":"BD96VWWdfcaHrF6cRYIHT8nwgpoGz6af7gf74z0yr9ymwMvy+2kLzT2VEoWXALHcidu1nPiHQUvj+17x60Hxk/4=","auth_pubkey_b64":"BGW7C/7hcVcMI1SKMeRqGoloV/0a2HWN9QYzsNtq6KGPMMOpqbp/FNuYTYn5NCJY6i7tPgqEFZXBmV6ahPEagpQ=","manufacturer_sig_b64":"DsN6XFwRNJW6pkJT67GWxMaEh8YNrt9FjG94jT9pitGMMZjMNBDAgEw0DpxxlBc2sbsFKtyVyaPI05Kesz9Zfw==","attestation_digest_hex":"e1505ba501c22df2875fa58a5a28c5b078c7cf6752d9ceb0ea2454a2e8373840","infineon_certs_der_b64":["MIIB2TCCAX6gAwIBAgIETu1GvDAKBggqhkjOPQQDAjByMQswCQYDVQQGEwJERTEhMB8GA1UECgwYSW5maW5lb24gVGVjaG5vbG9naWVzIEFHMRMwEQYDVQQLDApPUFRJR0EoVE0pMSswKQYDVQQDDCJJbmZpbmVvbiBPUFRJR0EoVE0pIFRydXN0IE0gQ0EgMTAxMB4XDTE5MDcwMTE1MDQxM1oXDTM5MDcwMTE1MDQxM1owHDEaMBgGA1UEAwwRSW5maW5lb24gSW9UIE5vZGUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARENGYNC3yfqufVgM3mTxa05KA/mmb/MnTF5q4aIZ2caMG8J++G5qCcMboaj49ktmHefrtS1FDjAybll96REqpPo1gwVjAOBgNVHQ8BAf8EBAMCAIAwDAYDVR0TAQH/BAIwADAVBgNVHSAEDjAMMAoGCCqCFABEARQBMB8GA1UdIwQYMBaAFDwwjFzViuijXTKA5FSDsv/Nhk0jMAoGCCqGSM49BAMCA0kAMEYCIQCAMPZZzrn8Gz5C9suTEdckxZoFxBCm+i7nI9NffXTxFgIhALSzBayRPfl983zRaJGqV/aKFcwWzLyrGGyOJYv5YfyO"],"efuse_status":{"blk3_provisioned":false,"blk3_write_protected":false,"secure_boot_enabled":false,"pad_jtag_disabled":false,"usb_jtag_disabled":false,"soft_jtag_disabled":false,"security_download_enabled":false,"usb_download_disabled":false,"secure_version":0}}"""

        val request = ProvisioningIngestRequest(rawPayload = rawPayload)
        val preview = service.verifyPreview(request)

        assertThat(preview.valid).isTrue()
        assertThat(preview.provisioningMode).isEqualTo("SOFT")
        assertThat(preview.hardwareId)
            .isEqualTo("cd16338401001c000100000a091b5c000b00820068801010710809")
        assertThat(preview.chainValid).isTrue()
        assertThat(preview.attestationDigestValid).isTrue()
        assertThat(preview.signatureValid).isTrue()
        assertThat(preview.errors).isEmpty()
    }

    @Test
    fun testIngestAndSave_UpdatesExistingLockSessionPublicKey() {
        val rawPayload =
            """SOFT_PROVISION_RESULT:{"status":"SOFT_PROVISIONED","session_token":"VLEPLJ","copr_uid_hex":"cd16338401001c000100000a091b5c000b00820068801010710809","lock_pubkey_b64":"BD96VWWdfcaHrF6cRYIHT8nwgpoGz6af7gf74z0yr9ymwMvy+2kLzT2VEoWXALHcidu1nPiHQUvj+17x60Hxk/4=","auth_pubkey_b64":"BGW7C/7hcVcMI1SKMeRqGoloV/0a2HWN9QYzsNtq6KGPMMOpqbp/FNuYTYn5NCJY6i7tPgqEFZXBmV6ahPEagpQ=","manufacturer_sig_b64":"DsN6XFwRNJW6pkJT67GWxMaEh8YNrt9FjG94jT9pitGMMZjMNBDAgEw0DpxxlBc2sbsFKtyVyaPI05Kesz9Zfw==","attestation_digest_hex":"e1505ba501c22df2875fa58a5a28c5b078c7cf6752d9ceb0ea2454a2e8373840","infineon_certs_der_b64":["MIIB2TCCAX6gAwIBAgIETu1GvDAKBggqhkjOPQQDAjByMQswCQYDVQQGEwJERTEhMB8GA1UECgwYSW5maW5lb24gVGVjaG5vbG9naWVzIEFHMRMwEQYDVQQLDApPUFRJR0EoVE0pMSswKQYDVQQDDCJJbmZpbmVvbiBPUFRJR0EoVE0pIFRydXN0IE0gQ0EgMTAxMB4XDTE5MDcwMTE1MDQxM1oXDTM5MDcwMTE1MDQxM1owHDEaMBgGA1UEAwwRSW5maW5lb24gSW9UIE5vZGUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARENGYNC3yfqufVgM3mTxa05KA/mmb/MnTF5q4aIZ2caMG8J++G5qCcMboaj49ktmHefrtS1FDjAybll96REqpPo1gwVjAOBgNVHQ8BAf8EBAMCAIAwDAYDVR0TAQH/BAIwADAVBgNVHSAEDjAMMAoGCCqCFABEARQBMB8GA1UdIwQYMBaAFDwwjFzViuijXTKA5FSDsv/Nhk0jMAoGCCqGSM49BAMCA0kAMEYCIQCAMPZZzrn8Gz5C9suTEdckxZoFxBCm+i7nI9NffXTxFgIhALSzBayRPfl983zRaJGqV/aKFcwWzLyrGGyOJYv5YfyO"],"efuse_status":{"blk3_provisioned":false,"blk3_write_protected":false,"secure_boot_enabled":false,"pad_jtag_disabled":false,"usb_jtag_disabled":false,"soft_jtag_disabled":false,"security_download_enabled":false,"usb_download_disabled":false,"secure_version":0}}"""

        val existingSession =
            LockSession(
                publicKey = "OLD_STALE_KEY",
                sessionToken = "VLEPLJ",
                shareToken = "s-123",
                totalControlToken = "tc-123",
            )
        `when`(lockSessionRepository.findBySessionToken("VLEPLJ")).thenReturn(existingSession)
        `when`(unitRepository.findByHardwareId(anyString())).thenReturn(null)
        `when`(unitRepository.save(any(ProvisionedHardwareUnit::class.java))).thenAnswer {
            it.arguments[0]
        }

        val request = ProvisioningIngestRequest(rawPayload = rawPayload)
        service.ingestAndSave(request)

        assertThat(existingSession.publicKey)
            .isEqualTo(
                "BD96VWWdfcaHrF6cRYIHT8nwgpoGz6af7gf74z0yr9ymwMvy+2kLzT2VEoWXALHcidu1nPiHQUvj+17x60Hxk/4="
            )
        verify(lockSessionRepository).save(existingSession)
    }
}
