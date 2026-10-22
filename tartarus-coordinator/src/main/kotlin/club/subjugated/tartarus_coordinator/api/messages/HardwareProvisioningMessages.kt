package club.subjugated.tartarus_coordinator.api.messages

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime

data class EfuseStatusPayload(
    @JsonProperty("blk3_provisioned") val blk3Provisioned: Boolean = false,
    @JsonProperty("blk3_write_protected") val blk3WriteProtected: Boolean = false,
    @JsonProperty("secure_boot_enabled") val secureBootEnabled: Boolean = false,
    @JsonProperty("pad_jtag_disabled") val padJtagDisabled: Boolean = false,
    @JsonProperty("usb_jtag_disabled") val usbJtagDisabled: Boolean = false,
    @JsonProperty("soft_jtag_disabled") val softJtagDisabled: Boolean = false,
    @JsonProperty("security_download_enabled") val securityDownloadEnabled: Boolean = false,
    @JsonProperty("usb_download_disabled") val usbDownloadDisabled: Boolean = false,
    @JsonProperty("secure_version") val secureVersion: Int = 0,
)

data class ProvisioningBlob(
    val status: String,
    @JsonProperty("session_token") val sessionToken: String,
    @JsonProperty("copr_uid_hex") val coprUidHex: String,
    @JsonProperty("lock_pubkey_b64") val lockPubkeyB64: String? = null,
    @JsonProperty("auth_pubkey_b64") val authPubkeyB64: String,
    @JsonProperty("manufacturer_sig_b64") val manufacturerSigB64: String,
    @JsonProperty("attestation_digest_hex") val attestationDigestHex: String,
    @JsonProperty("infineon_certs_der_b64") val infineonCertsDerB64: List<String>,
    @JsonProperty("efuse_status") val efuseStatus: EfuseStatusPayload? = null,
)

data class ProvisioningIngestRequest(
    val rawPayload: String? = null,
    val label: String? = null,
    val notes: String? = null,
    val status: String? = null,
    @JsonProperty("session_token") val sessionToken: String? = null,
    @JsonProperty("copr_uid_hex") val coprUidHex: String? = null,
    @JsonProperty("lock_pubkey_b64") val lockPubkeyB64: String? = null,
    @JsonProperty("auth_pubkey_b64") val authPubkeyB64: String? = null,
    @JsonProperty("manufacturer_sig_b64") val manufacturerSigB64: String? = null,
    @JsonProperty("attestation_digest_hex") val attestationDigestHex: String? = null,
    @JsonProperty("infineon_certs_der_b64") val infineonCertsDerB64: List<String>? = null,
    @JsonProperty("efuse_status") val efuseStatus: EfuseStatusPayload? = null,
)

data class ProvisioningPreviewResponse(
    val valid: Boolean,
    val provisioningMode: String,
    val hardwareId: String,
    val sessionToken: String,
    val leafCertSubject: String,
    val leafCertIssuer: String,
    val leafCertSerialHex: String,
    val chainValid: Boolean,
    val attestationDigestValid: Boolean,
    val signatureValid: Boolean,
    val efuseStatus: EfuseStatusPayload?,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
)

data class ProvisionedHardwareUnitSummaryDto(
    val hardwareId: String,
    val provisioningMode: String,
    val label: String?,
    val initialSessionToken: String,
    val lastKnownSessionToken: String?,
    val lastFirmwareVersion: String?,
    @JsonFormat(shape = JsonFormat.Shape.STRING) val lastAttestedAt: OffsetDateTime?,
    val isRevoked: Boolean,
    @JsonFormat(shape = JsonFormat.Shape.STRING) val provisionedAt: OffsetDateTime,
    @JsonFormat(shape = JsonFormat.Shape.STRING) val updatedAt: OffsetDateTime,
)

data class ProvisionedHardwareUnitDetailDto(
    val hardwareId: String,
    val provisioningMode: String,
    val authPubkeyB64: String,
    val manufacturerSigB64: String,
    val attestationDigestHex: String,
    val infineonCertsDerB64: List<String>,
    val leafCertSubject: String,
    val leafCertIssuer: String,
    val leafCertSerialHex: String,
    val blk3Provisioned: Boolean,
    val blk3WriteProtected: Boolean,
    val secureBootEnabled: Boolean,
    val padJtagDisabled: Boolean,
    val usbJtagDisabled: Boolean,
    val softJtagDisabled: Boolean,
    val securityDownloadEnabled: Boolean,
    val usbDownloadDisabled: Boolean,
    val secureVersion: Int,
    val initialSessionToken: String,
    val lastKnownSessionToken: String?,
    val lastKnownLockPubkeyB64: String?,
    val lastFirmwareVersion: String?,
    @JsonFormat(shape = JsonFormat.Shape.STRING) val lastAttestedAt: OffsetDateTime?,
    val label: String?,
    val notes: String?,
    val isRevoked: Boolean,
    val revokedReason: String?,
    @JsonFormat(shape = JsonFormat.Shape.STRING) val provisionedAt: OffsetDateTime,
    @JsonFormat(shape = JsonFormat.Shape.STRING) val updatedAt: OffsetDateTime,
    val attestationLogs: List<HardwareAttestationLogDto> = emptyList(),
)

data class HardwareAttestationLogDto(
    val id: Long,
    val hardwareId: String?,
    val sessionToken: String,
    val firmwareVersion: String?,
    val verificationMethod: String,
    val success: Boolean,
    val failureReason: String?,
    @JsonFormat(shape = JsonFormat.Shape.STRING) val createdAt: OffsetDateTime,
)

data class UpdateHardwareUnitRequest(
    val label: String? = null,
    val notes: String? = null,
    val isRevoked: Boolean? = null,
    val revokedReason: String? = null,
)
