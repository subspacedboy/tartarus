package club.subjugated.overlord_exe.services

import club.subjugated.overlord_exe.util.TimeSource
import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

sealed class MessageCheckResult {
    data object Allowed : MessageCheckResult()
    data class Rejected(val reason: String) : MessageCheckResult()
}

@Component
class IntentRateLimiter @Autowired constructor(
    @Value("\${overlord.bedrock.limits.max-message-length:500}")
    val maxMessageLength: Int,

    @Value("\${overlord.bedrock.limits.convo-rate-limit-count:5}")
    val convoRateLimitCount: Int,

    @Value("\${overlord.bedrock.limits.convo-rate-limit-window-seconds:30}")
    val convoRateLimitWindowSeconds: Long,

    @Value("\${overlord.bedrock.limits.convo-min-interval-seconds:2}")
    val convoMinIntervalSeconds: Long,

    @Value("\${overlord.bedrock.limits.global-rate-limit-count:60}")
    val globalRateLimitCount: Int,

    @Value("\${overlord.bedrock.limits.global-rate-limit-window-seconds:60}")
    val globalRateLimitWindowSeconds: Long,

    @Autowired(required = false)
    val timeSource: TimeSource = TimeSource()
) {
    constructor() : this(500, 5, 30L, 2L, 60, 60L, TimeSource())

    private val logger: Logger = LoggerFactory.getLogger(IntentRateLimiter::class.java)

    internal class ConvoTracker {
        val timestamps: ArrayDeque<Long> = ArrayDeque()
        var lastRequestTime: Long = 0L
    }

    private val convoTrackers = Caffeine.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build<String, ConvoTracker>()

    private val globalTimestamps = ArrayDeque<Long>()
    private val globalLock = Any()

    fun checkAndRecord(convoId: String, message: String): MessageCheckResult {
        if (message.isBlank()) {
            return MessageCheckResult.Rejected("Please send a valid message.")
        }

        if (message.length > maxMessageLength) {
            logger.warn("Message rejected: length ${message.length} exceeds limit $maxMessageLength for convo $convoId")
            return MessageCheckResult.Rejected(
                "Message is too long (${message.length} characters). Please keep your message under $maxMessageLength characters."
            )
        }

        val now = timeSource.nowInUtc().toInstant().toEpochMilli()

        // Check global rate limit
        synchronized(globalLock) {
            val globalCutoff = now - (globalRateLimitWindowSeconds * 1000)
            while (globalTimestamps.isNotEmpty() && globalTimestamps.peekFirst() < globalCutoff) {
                globalTimestamps.removeFirst()
            }
            if (globalTimestamps.size >= globalRateLimitCount) {
                logger.warn("Global Bedrock rate limit ($globalRateLimitCount / ${globalRateLimitWindowSeconds}s) exceeded.")
                return MessageCheckResult.Rejected(
                    "The system is currently handling too many requests. Please wait a moment before trying again."
                )
            }
        }

        // Check per-conversation rate limits
        val tracker = convoTrackers.get(convoId) { ConvoTracker() }
        synchronized(tracker) {
            if (convoMinIntervalSeconds > 0 && tracker.lastRequestTime > 0) {
                val elapsedSinceLast = now - tracker.lastRequestTime
                if (elapsedSinceLast < convoMinIntervalSeconds * 1000) {
                    logger.warn("Rate limit min interval ($convoMinIntervalSeconds s) violated for convo $convoId")
                    return MessageCheckResult.Rejected(
                        "You are sending messages too quickly. Please wait a moment before sending another message."
                    )
                }
            }

            val convoCutoff = now - (convoRateLimitWindowSeconds * 1000)
            while (tracker.timestamps.isNotEmpty() && tracker.timestamps.peekFirst() < convoCutoff) {
                tracker.timestamps.removeFirst()
            }
            if (tracker.timestamps.size >= convoRateLimitCount) {
                logger.warn("Conversation rate limit ($convoRateLimitCount / ${convoRateLimitWindowSeconds}s) reached for convo $convoId")
                return MessageCheckResult.Rejected(
                    "Rate limit reached. Please wait a few moments before trying again."
                )
            }

            tracker.timestamps.addLast(now)
            tracker.lastRequestTime = now
        }

        synchronized(globalLock) {
            globalTimestamps.addLast(now)
        }

        return MessageCheckResult.Allowed
    }

    fun reset() {
        convoTrackers.invalidateAll()
        synchronized(globalLock) {
            globalTimestamps.clear()
        }
    }
}
