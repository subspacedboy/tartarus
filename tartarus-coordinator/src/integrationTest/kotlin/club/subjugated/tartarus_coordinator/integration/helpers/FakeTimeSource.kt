package club.subjugated.tartarus_coordinator.integration.helpers

import club.subjugated.tartarus_coordinator.util.TimeSource
import java.time.OffsetDateTime
import java.time.ZonedDateTime

class FakeTimeSource(var now: OffsetDateTime) : TimeSource() {
    override fun nowInUtc(): OffsetDateTime {
        return now
    }

    override fun nowInZonedUTC(): ZonedDateTime {
        return nowInUtc().toZonedDateTime()
    }
}