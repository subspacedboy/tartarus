package club.subjugated.overlord_exe.services

import club.subjugated.overlord_exe.util.TimeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class TestTimeSource(var currentInstant: Instant = Instant.now()) : TimeSource() {
    override fun nowInUtc(): OffsetDateTime {
        return currentInstant.atOffset(ZoneOffset.UTC)
    }

    fun advanceSeconds(seconds: Long) {
        currentInstant = currentInstant.plusSeconds(seconds)
    }
}

class IntentRateLimiterTest {

    private lateinit var timeSource: TestTimeSource
    private lateinit var limiter: IntentRateLimiter

    @BeforeEach
    fun setUp() {
        timeSource = TestTimeSource(Instant.parse("2026-10-19T12:00:00Z"))
        limiter = IntentRateLimiter(
            maxMessageLength = 50,
            convoRateLimitCount = 3,
            convoRateLimitWindowSeconds = 10,
            convoMinIntervalSeconds = 2,
            globalRateLimitCount = 5,
            globalRateLimitWindowSeconds = 60,
            timeSource = timeSource
        )
    }

    @Test
    fun `rejects blank message`() {
        val result = limiter.checkAndRecord("convo-1", "   ")
        assertTrue(result is MessageCheckResult.Rejected)
        assertEquals("Please send a valid message.", (result as MessageCheckResult.Rejected).reason)
    }

    @Test
    fun `rejects message exceeding max length`() {
        val longMessage = "a".repeat(51)
        val result = limiter.checkAndRecord("convo-1", longMessage)
        assertTrue(result is MessageCheckResult.Rejected)
        assertTrue((result as MessageCheckResult.Rejected).reason.contains("too long"))
    }

    @Test
    fun `allows valid message within length`() {
        val result = limiter.checkAndRecord("convo-1", "Hello Overlord")
        assertTrue(result is MessageCheckResult.Allowed)
    }

    @Test
    fun `enforces minimum interval between consecutive messages in same convo`() {
        val r1 = limiter.checkAndRecord("convo-1", "First message")
        assertTrue(r1 is MessageCheckResult.Allowed)

        // Sending immediately (< 2 seconds min interval)
        timeSource.advanceSeconds(1)
        val r2 = limiter.checkAndRecord("convo-1", "Second message too fast")
        assertTrue(r2 is MessageCheckResult.Rejected)
        assertTrue((r2 as MessageCheckResult.Rejected).reason.contains("too quickly"))

        // After waiting past min interval (total 2s from first)
        timeSource.advanceSeconds(1)
        val r3 = limiter.checkAndRecord("convo-1", "Third message ok")
        assertTrue(r3 is MessageCheckResult.Allowed)
    }

    @Test
    fun `enforces per-conversation window rate limit`() {
        // Limit is 3 requests per 10 seconds, with 2s min interval
        // Request 1 at t=0
        assertTrue(limiter.checkAndRecord("convo-1", "Msg 1") is MessageCheckResult.Allowed)

        // Request 2 at t=2
        timeSource.advanceSeconds(2)
        assertTrue(limiter.checkAndRecord("convo-1", "Msg 2") is MessageCheckResult.Allowed)

        // Request 3 at t=4
        timeSource.advanceSeconds(2)
        assertTrue(limiter.checkAndRecord("convo-1", "Msg 3") is MessageCheckResult.Allowed)

        // Request 4 at t=6 -> 4th request in 10-second window -> should be rejected
        timeSource.advanceSeconds(2)
        val r4 = limiter.checkAndRecord("convo-1", "Msg 4")
        assertTrue(r4 is MessageCheckResult.Rejected)
        assertTrue((r4 as MessageCheckResult.Rejected).reason.contains("Rate limit reached"))

        // Advance past window (t=11, which is > 10s from t=0)
        timeSource.advanceSeconds(5) // now at t=11
        // Request 1 (t=0) fell off the window. Now we should be allowed again!
        val r5 = limiter.checkAndRecord("convo-1", "Msg 5")
        assertTrue(r5 is MessageCheckResult.Allowed)
    }

    @Test
    fun `rate limits are tracked separately per conversation`() {
        // convo-1 makes a request at t=0
        assertTrue(limiter.checkAndRecord("convo-1", "Msg from 1") is MessageCheckResult.Allowed)

        // convo-2 should not be blocked by convo-1's min interval at t=0
        assertTrue(limiter.checkAndRecord("convo-2", "Msg from 2") is MessageCheckResult.Allowed)
    }

    @Test
    fun `enforces global rate limit across different conversations`() {
        // Global limit is 5 requests per 60 seconds
        assertTrue(limiter.checkAndRecord("c1", "Msg 1") is MessageCheckResult.Allowed)
        timeSource.advanceSeconds(3)
        assertTrue(limiter.checkAndRecord("c2", "Msg 2") is MessageCheckResult.Allowed)
        timeSource.advanceSeconds(3)
        assertTrue(limiter.checkAndRecord("c3", "Msg 3") is MessageCheckResult.Allowed)
        timeSource.advanceSeconds(3)
        assertTrue(limiter.checkAndRecord("c4", "Msg 4") is MessageCheckResult.Allowed)
        timeSource.advanceSeconds(3)
        assertTrue(limiter.checkAndRecord("c5", "Msg 5") is MessageCheckResult.Allowed)

        // 6th request from c6 should be rejected by global limiter
        timeSource.advanceSeconds(3)
        val r6 = limiter.checkAndRecord("c6", "Msg 6")
        assertTrue(r6 is MessageCheckResult.Rejected)
        assertTrue((r6 as MessageCheckResult.Rejected).reason.contains("too many requests"))

        // Advance past global window (60s)
        timeSource.advanceSeconds(65)
        assertTrue(limiter.checkAndRecord("c6", "Msg 6 retry") is MessageCheckResult.Allowed)
    }
}
