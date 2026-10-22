package club.subjugated.tartarus_coordinator.models

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(
    name = "provisioned_hardware_units",
    indexes =
        [
            Index(name = "idx_provisioned_units_session", columnList = "last_known_session_token"),
            Index(name = "idx_provisioned_units_mode", columnList = "provisioning_mode"),
        ],
)
class ProvisionedHardwareUnit(
    @Id @Column(name = "hardware_id", length = 64) var hardwareId: String,
    @Column(name = "provisioning_mode", length = 16, nullable = false)
    var provisioningMode: String, // 'HARD' or 'SOFT'
    @Column(name = "auth_pubkey_b64", length = 255, nullable = false, unique = true)
    var authPubkeyB64: String,
    @Lob
    @Column(name = "manufacturer_sig_b64", columnDefinition = "TEXT", nullable = false)
    var manufacturerSigB64: String,
    @Column(name = "attestation_digest_hex", length = 64, nullable = false)
    var attestationDigestHex: String,
    @Lob
    @Column(name = "infineon_certs_json", columnDefinition = "TEXT", nullable = false)
    var infineonCertsJson: String,
    @Lob
    @Column(name = "leaf_cert_subject", columnDefinition = "TEXT", nullable = false)
    var leafCertSubject: String,
    @Lob
    @Column(name = "leaf_cert_issuer", columnDefinition = "TEXT", nullable = false)
    var leafCertIssuer: String,
    @Column(name = "leaf_cert_serial_hex", length = 128, nullable = false)
    var leafCertSerialHex: String,

    // Snapshot of ESP32-S3 eFuse security posture
    @Column(name = "blk3_provisioned", nullable = false) var blk3Provisioned: Boolean = false,
    @Column(name = "blk3_write_protected", nullable = false)
    var blk3WriteProtected: Boolean = false,
    @Column(name = "secure_boot_enabled", nullable = false) var secureBootEnabled: Boolean = false,
    @Column(name = "pad_jtag_disabled", nullable = false) var padJtagDisabled: Boolean = false,
    @Column(name = "usb_jtag_disabled", nullable = false) var usbJtagDisabled: Boolean = false,
    @Column(name = "soft_jtag_disabled", nullable = false) var softJtagDisabled: Boolean = false,
    @Column(name = "security_download_enabled", nullable = false)
    var securityDownloadEnabled: Boolean = false,
    @Column(name = "usb_download_disabled", nullable = false)
    var usbDownloadDisabled: Boolean = false,
    @Column(name = "secure_version", nullable = false) var secureVersion: Int = 0,

    // Mutable / rotatable operational state
    @Column(name = "initial_session_token", length = 32, nullable = false)
    var initialSessionToken: String,
    @Column(name = "last_known_session_token", length = 32)
    var lastKnownSessionToken: String? = null,
    @Lob
    @Column(name = "last_known_lock_pubkey_b64", columnDefinition = "TEXT")
    var lastKnownLockPubkeyB64: String? = null,
    @Column(name = "last_firmware_version", length = 64) var lastFirmwareVersion: String? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = "last_attested_at")
    var lastAttestedAt: OffsetDateTime? = null,

    // Administrative metadata
    @Column(name = "label", length = 128) var label: String? = null,
    @Lob @Column(name = "notes", columnDefinition = "TEXT") var notes: String? = null,
    @Column(name = "is_revoked", nullable = false) var isRevoked: Boolean = false,
    @Lob
    @Column(name = "revoked_reason", columnDefinition = "TEXT")
    var revokedReason: String? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = "provisioned_at", nullable = false)
    var provisionedAt: OffsetDateTime = OffsetDateTime.now(),
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
