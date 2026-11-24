package club.subjugated.overlord_exe.models

import club.subjugated.overlord_exe.util.generateId
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PostLoad
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.Transient
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime

object OffsetDateTimeIsoSerializer : KSerializer<OffsetDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: OffsetDateTime) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): OffsetDateTime {
        return OffsetDateTime.parse(decoder.decodeString())
    }
}

val MachineJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "_type"
}

enum class SessionPhase {
    CREATED,
    ISSUED,
    ACTIVE,
    COMPLETE,
    ABORTED,
}

enum class ReleasePolicy {
    ALL,
    ANY,
    MANUAL,
}

enum class MachineStatus {
    NEEDS_INFO,
    WAITING_FORM,
    RUNNING,
    SATISFIED,
    FAILED,
}

@Serializable
enum class CrowdTimePhase {
    UNSPECIFIED,
    CREATED,
    NEEDS_INFO,
    WAITING,
    OPEN_POSTED,
    IN_OPEN,
    OPEN_CLOSED,
    COMPLETE,
}

@Serializable
sealed interface MachineState {
    val machineType: String
}

@Serializable
@SerialName("timer")
data class TimerMachineState(
    var isRandom: Boolean = false,
    var minDuration: Int = 1,
    var minDurationUnit: String = "HOURS",
    var maxDuration: Int = 1,
    var maxDurationUnit: String = "HOURS",
    @Serializable(with = OffsetDateTimeIsoSerializer::class)
    var endsAt: OffsetDateTime? = null,
    var completed: Boolean = false,
) : MachineState {
    override val machineType: String get() = "timer"
}

@Serializable
@SerialName("proxy_control")
data class ProxyControlMachineState(
    var keyholderDid: String = "",
    var keyholderHandle: String = "",
    var keyholderConvoId: String = "",
    var subHandle: String = "",
    var allowAutoRelease: Boolean = false,
) : MachineState {
    override val machineType: String get() = "proxy_control"
}

@Serializable
@SerialName("bsky_likes")
data class BSkyLikesMachineState(
    var did: String = "",
    var goal: Long = 100,
    var likesSoFar: Long = 0,
    var receivedFormData: Boolean = false,
    @Serializable(with = OffsetDateTimeIsoSerializer::class)
    var createdAt: OffsetDateTime? = null,
) : MachineState {
    override val machineType: String get() = "bsky_likes"
}

@Serializable
@SerialName("bsky_crowd_time")
data class BSkyCrowdTimeMachineState(
    var phase: CrowdTimePhase = CrowdTimePhase.CREATED,
    var name: String = generateId("ct-"),
    var subjectDid: String = "",
    var perLikeAdd: Long = 1,
    var perLikeAddUnit: String = "MINUTES",
    var perRepostAdd: Long = 1,
    var perRepostAddUnit: String = "HOURS",
    var openPeriodAmount: Long = 1,
    var openPeriodUnit: String = "HOURS",
    var noticeUri: String = "",
    var hasRepostedNotice: Boolean = false,
    var receivedFormData: Boolean = false,
    @Serializable(with = OffsetDateTimeIsoSerializer::class)
    var createdAt: OffsetDateTime? = null,
    @Serializable(with = OffsetDateTimeIsoSerializer::class)
    var acceptedAt: OffsetDateTime? = null,
    @Serializable(with = OffsetDateTimeIsoSerializer::class)
    var repostedNoticeAt: OffsetDateTime? = null,
    @Serializable(with = OffsetDateTimeIsoSerializer::class)
    var openEndsAt: OffsetDateTime? = null,
    @Serializable(with = OffsetDateTimeIsoSerializer::class)
    var endsAt: OffsetDateTime? = null,
) : MachineState {
    override val machineType: String get() = "bsky_crowd_time"
    val state: String get() = phase.name
}

@Entity
@Table(name = "contract_session")
class ContractSession(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    var token: String = generateId("ses-"),
    var botId: String = "overlord",
    @Enumerated(EnumType.STRING)
    var phase: SessionPhase = SessionPhase.CREATED,
    @Enumerated(EnumType.STRING)
    var releasePolicy: ReleasePolicy = ReleasePolicy.ALL,
    var isPublic: Boolean = false,
    var terms: String? = null,
    var contractId: Long? = null,
    var contractSerialNumber: Int = 0,
    var shareableToken: String? = null,
    var subjectDid: String,
    var convoId: String = "",
    var keyholderDid: String? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var acceptedAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var completedAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var updatedAt: OffsetDateTime? = null,
)

@Entity
@Table(name = "session_machine")
class SessionMachine(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    var sessionId: Long,
    var machineType: String,
    @Enumerated(EnumType.STRING)
    var status: MachineStatus = MachineStatus.NEEDS_INFO,
    var infoToken: String? = null,
    var statusToken: String? = null,
    @Column(name = "state_json", columnDefinition = "json")
    var stateJson: String = "",
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var updatedAt: OffsetDateTime? = null,
    state: MachineState = TimerMachineState(),
) {
    @Transient
    private var cachedState: MachineState? = state

    var state: MachineState
        get() {
            if (cachedState == null && stateJson.isNotBlank()) {
                cachedState = MachineJson.decodeFromString<MachineState>(stateJson)
            }
            return cachedState ?: TimerMachineState().also { cachedState = it }
        }
        set(value) {
            cachedState = value
            stateJson = MachineJson.encodeToString<MachineState>(value)
        }

    init {
        if (stateJson.isBlank()) {
            stateJson = MachineJson.encodeToString<MachineState>(state)
        } else {
            cachedState = runCatching { MachineJson.decodeFromString<MachineState>(stateJson) }.getOrNull() ?: state
        }
    }

    @PostLoad
    fun onPostLoad() {
        if (stateJson.isNotBlank()) {
            cachedState = MachineJson.decodeFromString<MachineState>(stateJson)
        }
    }

    @PrePersist
    @PreUpdate
    fun syncJsonBeforeSave() {
        cachedState?.let {
            stateJson = MachineJson.encodeToString<MachineState>(it)
        }
    }
}
