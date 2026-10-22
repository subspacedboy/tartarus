package club.subjugated.tartarus_coordinator.api.admin

import club.subjugated.tartarus_coordinator.api.messages.*
import club.subjugated.tartarus_coordinator.services.HardwareVerificationService
import java.time.OffsetDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus

class HardwareProvisioningAdminControllerTest {

    private lateinit var verificationService: HardwareVerificationService
    private lateinit var controller: HardwareProvisioningAdminController

    @BeforeEach
    fun setUp() {
        verificationService = mock(HardwareVerificationService::class.java)
        controller = HardwareProvisioningAdminController(verificationService)
    }

    @Test
    fun testProvisionEndpoint() {
        val request = ProvisioningIngestRequest(rawPayload = "PROVISION_RESULT:{}")
        val expectedDetail =
            ProvisionedHardwareUnitDetailDto(
                hardwareId = "cd16338201001c00010000000a1b2c3d4e5f60718293a4b5c6d7e8",
                provisioningMode = "HARD",
                authPubkeyB64 = "auth",
                manufacturerSigB64 = "sig",
                attestationDigestHex = "digest",
                infineonCertsDerB64 = emptyList(),
                leafCertSubject = "leaf",
                leafCertIssuer = "issuer",
                leafCertSerialHex = "123",
                blk3Provisioned = true,
                blk3WriteProtected = true,
                secureBootEnabled = true,
                padJtagDisabled = true,
                usbJtagDisabled = true,
                softJtagDisabled = true,
                securityDownloadEnabled = true,
                usbDownloadDisabled = false,
                secureVersion = 1,
                initialSessionToken = "ABC234",
                lastKnownSessionToken = "ABC234",
                lastKnownLockPubkeyB64 = null,
                lastFirmwareVersion = null,
                lastAttestedAt = null,
                label = "Production Unit",
                notes = "Shipped",
                isRevoked = false,
                revokedReason = null,
                provisionedAt = OffsetDateTime.now(),
                updatedAt = OffsetDateTime.now(),
            )

        `when`(verificationService.ingestAndSave(request)).thenReturn(expectedDetail)

        val response = controller.provisionUnit(request)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.hardwareId)
            .isEqualTo("cd16338201001c00010000000a1b2c3d4e5f60718293a4b5c6d7e8")
    }

    @Test
    fun testVerifyPreviewEndpoint() {
        val request = ProvisioningIngestRequest(rawPayload = "SOFT_PROVISION_RESULT:{}")
        val preview =
            ProvisioningPreviewResponse(
                valid = true,
                provisioningMode = "SOFT",
                hardwareId = "cd16338201001c00010000000a1b2c3d4e5f60718293a4b5c6d7e8",
                sessionToken = "ABC234",
                leafCertSubject = "CN=OPTIGA",
                leafCertIssuer = "CN=CA",
                leafCertSerialHex = "123",
                chainValid = true,
                attestationDigestValid = true,
                signatureValid = true,
                efuseStatus = null,
            )

        `when`(verificationService.verifyPreview(request)).thenReturn(preview)

        val response = controller.verifyPreview(request)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.valid).isTrue()
    }

    @Test
    fun testListUnitsEndpoint() {
        val summary =
            ProvisionedHardwareUnitSummaryDto(
                hardwareId = "cd16338201001c00010000000a1b2c3d4e5f60718293a4b5c6d7e8",
                provisioningMode = "HARD",
                label = "Unit 1",
                initialSessionToken = "ABC234",
                lastKnownSessionToken = "DEF567",
                lastFirmwareVersion = "1.0.0",
                lastAttestedAt = OffsetDateTime.now(),
                isRevoked = false,
                provisionedAt = OffsetDateTime.now(),
                updatedAt = OffsetDateTime.now(),
            )

        `when`(verificationService.listUnits("HARD", false, null)).thenReturn(listOf(summary))

        val response = controller.listUnits("HARD", false, null)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).hasSize(1)
        assertThat(response.body?.get(0)?.hardwareId)
            .isEqualTo("cd16338201001c00010000000a1b2c3d4e5f60718293a4b5c6d7e8")
    }
}
