package club.subjugated.overlord_exe.services

import club.subjugated.overlord_exe.models.UserProfile
import club.subjugated.overlord_exe.models.UserShareableToken
import club.subjugated.overlord_exe.storage.UserProfileRepository
import club.subjugated.overlord_exe.storage.UserShareableTokenRepository
import club.subjugated.overlord_exe.util.ShareableTokenType
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.extractAllShareableTokens
import club.subjugated.overlord_exe.util.extractShareableToken
import club.subjugated.overlord_exe.util.inferShareableTokenType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.time.Duration

sealed interface TokenResolution {
    data object None : TokenResolution
    data class Single(val token: UserShareableToken) : TokenResolution
    data class Multiple(val tokens: List<UserShareableToken>) : TokenResolution
}

@Service
class UserProfileService(
    private val userProfileRepository: UserProfileRepository,
    private val tokenRepository: UserShareableTokenRepository,
    private val timeSource: TimeSource,
    @Lazy @Autowired(required = false) private val blueSkyService: BlueSkyService? = null,
) {
    private val logger = LoggerFactory.getLogger(UserProfileService::class.java)

    fun save(profile: UserProfile): UserProfile {
        profile.syncJsonBeforeSave()
        profile.updatedAt = timeSource.nowInUtc()
        if (profile.createdAt == null) {
            profile.createdAt = profile.updatedAt
        }
        return userProfileRepository.save(profile)
    }

    fun findByDid(did: String): UserProfile? = userProfileRepository.findByDid(did)

    fun findOrCreateByDid(did: String): UserProfile {
        return userProfileRepository.findByDid(did) ?: save(
            UserProfile(
                did = did,
                createdAt = timeSource.nowInUtc(),
                updatedAt = timeSource.nowInUtc(),
            )
        )
    }

    fun findByHandle(handle: String): UserProfile? {
        val normalized = normalizeHandle(handle)
        if (normalized.isBlank()) return null

        userProfileRepository.findByHandleIgnoreCase(normalized)?.let { return it }

        val bsky = blueSkyService ?: return null
        return runCatching {
            val did = bsky.resolveHandleToDid(normalized)
            if (did.isNotBlank()) {
                findOrCreateByDid(did).also { profile ->
                    if (profile.handle.isNullOrBlank()) {
                        profile.handle = normalized
                        save(profile)
                    }
                }
            } else {
                null
            }
        }.onFailure { ex ->
            logger.debug("Could not resolve handle '$normalized' via Bluesky: ${ex.message}")
        }.getOrNull()
    }

    fun findByShareableToken(rawToken: String): UserProfile? {
        val token = extractShareableToken(rawToken)
        if (token.isBlank()) return null
        val entry = tokenRepository.findFirstByTokenOrderByLastUsedAtDescIdDesc(token) ?: return null
        return userProfileRepository.findByDid(entry.userDid)
    }

    fun learnFromDm(
        did: String,
        convoId: String,
        handle: String?,
        messageText: String,
    ): UserProfile {
        val profile = findOrCreateByDid(did)
        var dirty = false

        if (convoId.isNotBlank() && profile.convoId != convoId) {
            profile.convoId = convoId
            dirty = true
        }
        if (!handle.isNullOrBlank()) {
            val normalized = normalizeHandle(handle)
            if (profile.handle != normalized) {
                profile.handle = normalized
                dirty = true
            }
        }

        // Refresh Bluesky list memberships lazily (at most once per hour)
        val now = timeSource.nowInUtc()
        val lastSynced = profile.lastSyncedAt
        if (blueSkyService != null && (lastSynced == null || Duration.between(lastSynced, now).toMinutes() >= 60)) {
            syncBlueskyLists(profile)
            dirty = true
        }

        val saved = if (dirty) save(profile) else profile

        // Passively learn any shareable tokens mentioned in DM text or URLs
        extractAllShareableTokens(messageText).forEach { token ->
            recordToken(did = did, rawToken = token)
        }

        return saved
    }

    fun recordToken(
        did: String,
        rawToken: String,
        label: String? = null,
        makeDefault: Boolean = false,
        markUsed: Boolean = false,
    ): UserShareableToken {
        findOrCreateByDid(did)
        val normalized = extractShareableToken(rawToken)
        require(normalized.isNotBlank()) { "Shareable token cannot be blank" }

        val now = timeSource.nowInUtc()
        val existing = tokenRepository.findByUserDidAndToken(did, normalized)
        if (existing != null) {
            if (!label.isNullOrBlank()) existing.label = label
            if (makeDefault) {
                clearDefaultToken(did)
                existing.isDefault = true
            }
            if (markUsed) existing.lastUsedAt = now
            existing.updatedAt = now
            return tokenRepository.save(existing)
        }

        if (makeDefault) {
            clearDefaultToken(did)
        }

        val entry = UserShareableToken(
            userDid = did,
            token = normalized,
            tokenType = inferShareableTokenType(normalized),
            label = label,
            isDefault = makeDefault,
            lastUsedAt = if (markUsed) now else null,
            createdAt = now,
            updatedAt = now,
        )
        return tokenRepository.save(entry)
    }

    private fun clearDefaultToken(did: String) {
        val current = tokenRepository.findByUserDidOrderByIsDefaultDescLastUsedAtDescIdDesc(did)
        current.filter { it.isDefault }.forEach {
            it.isDefault = false
            it.updatedAt = timeSource.nowInUtc()
            tokenRepository.save(it)
        }
    }

    fun getTokensForUser(did: String): List<UserShareableToken> {
        return tokenRepository.findByUserDidOrderByIsDefaultDescLastUsedAtDescIdDesc(did)
    }

    fun resolveTokenForUser(
        did: String,
        preferredType: ShareableTokenType? = null,
    ): TokenResolution {
        val tokens = getTokensForUser(did)
        if (tokens.isEmpty()) return TokenResolution.None
        if (tokens.size == 1) return TokenResolution.Single(tokens.first())

        if (preferredType != null) {
            val matching = tokens.filter { it.tokenType == preferredType }
            if (matching.size == 1) return TokenResolution.Single(matching.first())
        }

        tokens.firstOrNull { it.isDefault }?.let { return TokenResolution.Single(it) }
        return TokenResolution.Multiple(tokens)
    }

    fun recordSessionOutcome(
        subjectDid: String,
        completed: Boolean,
        aborted: Boolean = false,
        emergencyUnlocked: Boolean = false,
    ) {
        val profile = findOrCreateByDid(subjectDid)
        val stats = profile.stats
        if (completed) stats.completedContracts += 1
        if (aborted) stats.abortedContracts += 1
        if (emergencyUnlocked) stats.emergencyUnlockCount += 1
        profile.stats = stats
        save(profile)
    }

    fun updateDefaultPublic(did: String, isPublic: Boolean) {
        val profile = findOrCreateByDid(did)
        val prefs = profile.preferences
        prefs.defaultIsPublic = isPublic
        profile.preferences = prefs
        save(profile)
    }

    private fun syncBlueskyLists(profile: UserProfile) {
        val bsky = blueSkyService ?: return
        runCatching {
            val myLists = bsky.getMyLists()
            val memberOf = mutableListOf<String>()
            for (listView in myLists) {
                val listName = listView.name ?: continue
                val listUri = listView.uri ?: continue
                val response = bsky.getList(listUri)
                if (response.items.any { it.subject.did == profile.did }) {
                    memberOf.add(listName)
                }
            }
            profile.lists = memberOf
            profile.lastSyncedAt = timeSource.nowInUtc()
        }.onFailure { ex ->
            logger.debug("Skipping Bluesky list sync for ${profile.did}: ${ex.message}")
            profile.lastSyncedAt = timeSource.nowInUtc()
        }
    }

    private fun normalizeHandle(raw: String): String {
        return raw.trim().removePrefix("@").lowercase()
    }
}
