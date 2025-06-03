package club.subjugated.overlord_exe.integration.helpers

import club.subjugated.overlord_exe.util.TimeSource
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