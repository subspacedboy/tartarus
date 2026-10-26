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

val EngagementJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "_type"
}

enum class EngagementPhase {
    UNSPECIFIED,
    CREATED,
    ISSUED,
    ACTIVE,
    COMPLETE,
    ABORTED,
}

@Serializable
enum class ObjectiveStatus {
    NEEDS_INFO,
    WAITING,
    IN_PROGRESS,
    COMPLETE,
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
sealed interface ObjectiveState {
    val objectiveId: String
    var status: ObjectiveStatus
}

@Serializable
@SerialName("bsky_likes")
data class BSkyLikesObjectiveState(
    override val objectiveId: String = "bsky_likes",
    override var status: ObjectiveStatus = ObjectiveStatus.NEEDS_INFO,
    var did: String = "",
    var goal: Long = 100,
    var likesSoFar: Long = 0,
    var receivedFormData: Boolean = false,
    @Serializable(with = OffsetDateTimeIsoSerializer::class)
    var createdAt: OffsetDateTime? = null,
) : ObjectiveState

@Serializable
@SerialName("bsky_crowd_time")
data class BSkyCrowdTimeObjectiveState(
    override val objectiveId: String = "bsky_crowd_time",
    override var status: ObjectiveStatus = ObjectiveStatus.NEEDS_INFO,
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
) : ObjectiveState {
    // Exposed for Thymeleaf status template fallback compatibility
    val state: String get() = phase.name
}

@Serializable
sealed interface EngagementData

@Serializable
@SerialName("timer")
data class TimerEngagementData(
    var isPublic: Boolean = false,
    var isRandom: Boolean = false,
    var minDuration: Int = 0,
    var minDurationUnit: String = "MINUTES",
    var maxDuration: Int = 0,
    var maxDurationUnit: String = "MINUTES",
    @Serializable(with = OffsetDateTimeIsoSerializer::class)
    var endsAt: OffsetDateTime? = null,
    var completed: Boolean = false,
) : EngagementData

@Serializable
@SerialName("simple_proxy")
data class SimpleProxyEngagementData(
    var isPublic: Boolean = false,
    var keyholderHandle: String = "",
    var keyholderConvoId: String = "",
    var subHandle: String = "",
) : EngagementData

@Serializable
@SerialName("superbot")
data class SuperBotEngagementData(
    var isPublic: Boolean = false,
    var objectives: List<String> = emptyList(),
    var activeObjective: ObjectiveState? = null,
) : EngagementData

@Entity
@Table(name = "bot_engagement")
class BotEngagement(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    var name: String = generateId("eng-"),
    var botId: String = "",
    @Enumerated(EnumType.STRING)
    var phase: EngagementPhase = EngagementPhase.CREATED,
    var contractId: Long? = null,
    var contractSerialNumber: Int = 0,
    var shareableToken: String? = "",
    var subjectDid: String = "",
    var convoId: String = "",
    var keyholderDid: String? = null,
    var infoToken: String? = null,
    var statusToken: String? = null,
    @Column(name = "data", columnDefinition = "json")
    var rawDataJson: String = "",
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var acceptedAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var updatedAt: OffsetDateTime? = null,
    data: EngagementData = TimerEngagementData(),
) {
    @Transient
    private var cachedData: EngagementData? = data

    var data: EngagementData
        get() {
            if (cachedData == null && rawDataJson.isNotBlank()) {
                cachedData = EngagementJson.decodeFromString<EngagementData>(rawDataJson)
            }
            return cachedData ?: TimerEngagementData().also { cachedData = it }
        }
        set(value) {
            cachedData = value
            rawDataJson = EngagementJson.encodeToString<EngagementData>(value)
        }

    init {
        if (rawDataJson.isBlank()) {
            rawDataJson = EngagementJson.encodeToString<EngagementData>(data)
        } else {
            cachedData = runCatching { EngagementJson.decodeFromString<EngagementData>(rawDataJson) }.getOrNull() ?: data
        }
    }

    @PostLoad
    fun onPostLoad() {
        if (rawDataJson.isNotBlank()) {
            cachedData = EngagementJson.decodeFromString<EngagementData>(rawDataJson)
        }
    }

    @PrePersist
    @PreUpdate
    fun syncJsonBeforeSave() {
        cachedData?.let {
            rawDataJson = EngagementJson.encodeToString<EngagementData>(it)
        }
    }
}
