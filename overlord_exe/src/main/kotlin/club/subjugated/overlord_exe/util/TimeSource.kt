package club.subjugated.overlord_exe.util

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

open class TimeSource {
    open fun nowInUtc(): OffsetDateTime {
        return OffsetDateTime.now(ZoneOffset.UTC)
    }

    open fun nowInZonedUTC(): ZonedDateTime {
        return ZonedDateTime.now(ZoneId.of("UTC"))
    }
}
