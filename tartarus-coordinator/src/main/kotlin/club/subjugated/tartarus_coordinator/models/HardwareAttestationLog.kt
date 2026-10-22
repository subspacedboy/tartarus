package club.subjugated.tartarus_coordinator.models

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(
    name = "hardware_attestation_logs",
    indexes =
        [
            Index(
                name = "idx_attestation_logs_hardware_id",
                columnList = "hardware_id, created_at DESC",
            )
        ],
)
class HardwareAttestationLog(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    @Column(name = "hardware_id", length = 64) var hardwareId: String? = null,
    @Column(name = "session_token", length = 32, nullable = false) var sessionToken: String,
    @Column(name = "firmware_version", length = 64) var firmwareVersion: String? = null,
    @Column(name = "verification_method", length = 32, nullable = false)
    var verificationMethod: String, // 'OPTIGA_HARD', 'OPTIGA_SOFT', 'LEGACY_KEY', 'FAILED'
    @Column(name = "success", nullable = false) var success: Boolean,
    @Lob
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    var failureReason: String? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
)
