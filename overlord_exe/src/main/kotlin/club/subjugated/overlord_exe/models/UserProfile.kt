package club.subjugated.overlord_exe.models

import club.subjugated.overlord_exe.util.ShareableTokenType
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime

val ProfileJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
data class UserPreferences(
    var defaultIsPublic: Boolean? = null,
    var preferredMachines: List<String> = emptyList(),
)

@Serializable
data class UserStats(
    var completedContracts: Int = 0,
    var abortedContracts: Int = 0,
    var emergencyUnlockCount: Int = 0,
    var totalLockMinutes: Long = 0,
)

@Entity
@Table(name = "user_profile")
class UserProfile(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    var name: String = generateId("usr-"),
    var did: String,
    var handle: String? = "",
    var displayName: String? = null,
    var convoId: String? = "",
    var doNotContact: Boolean = false,
    @Column(name = "lists_json", columnDefinition = "json")
    var listsJson: String? = "[]",
    @Column(name = "preferences_json", columnDefinition = "json")
    var preferencesJson: String? = "{}",
    @Column(name = "stats_json", columnDefinition = "json")
    var statsJson: String? = "{}",
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var lastSyncedAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var updatedAt: OffsetDateTime? = null,
) {
    @Transient
    private var cachedLists: MutableList<String>? = null

    @Transient
    private var cachedPreferences: UserPreferences? = null

    @Transient
    private var cachedStats: UserStats? = null

    var lists: MutableList<String>
        get() {
            if (cachedLists == null) {
                cachedLists = listsJson?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { ProfileJson.decodeFromString<MutableList<String>>(it) }.getOrNull() }
                    ?: mutableListOf()
            }
            return cachedLists!!
        }
        set(value) {
            cachedLists = value
            listsJson = ProfileJson.encodeToString(value)
        }

    var preferences: UserPreferences
        get() {
            if (cachedPreferences == null) {
                cachedPreferences = preferencesJson?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { ProfileJson.decodeFromString<UserPreferences>(it) }.getOrNull() }
                    ?: UserPreferences()
            }
            return cachedPreferences!!
        }
        set(value) {
            cachedPreferences = value
            preferencesJson = ProfileJson.encodeToString(value)
        }

    var stats: UserStats
        get() {
            if (cachedStats == null) {
                cachedStats = statsJson?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { ProfileJson.decodeFromString<UserStats>(it) }.getOrNull() }
                    ?: UserStats()
            }
            return cachedStats!!
        }
        set(value) {
            cachedStats = value
            statsJson = ProfileJson.encodeToString(value)
        }

    @PostLoad
    fun onPostLoad() {
        cachedLists = null
        cachedPreferences = null
        cachedStats = null
    }

    @PrePersist
    @PreUpdate
    fun syncJsonBeforeSave() {
        cachedLists?.let { listsJson = ProfileJson.encodeToString(it) }
        cachedPreferences?.let { preferencesJson = ProfileJson.encodeToString(it) }
        cachedStats?.let { statsJson = ProfileJson.encodeToString(it) }
    }
}

@Entity
@Table(name = "user_shareable_token")
class UserShareableToken(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    var userDid: String,
    var token: String,
    @Enumerated(EnumType.STRING)
    var tokenType: ShareableTokenType = ShareableTokenType.OTHER,
    var label: String? = null,
    var isDefault: Boolean = false,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var lastUsedAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var updatedAt: OffsetDateTime? = null,
)

enum class ConversationMessageRole {
    USER,
    ASSISTANT,
    TOOL,
}

@Entity
@Table(name = "conversation_message")
class ConversationMessage(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    var userDid: String,
    var convoId: String,
    @Enumerated(EnumType.STRING)
    var role: ConversationMessageRole,
    var content: String,
    var toolName: String? = null,
    var inputTokens: Int? = null,
    var outputTokens: Int? = null,
    var modelId: String? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var createdAt: OffsetDateTime,
)
