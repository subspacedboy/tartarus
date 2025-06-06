package club.subjugated.overlord_exe.util

import java.time.temporal.ChronoUnit

fun formatDuration(amount: Long, unit: String): String {
    val chronoUnit = ChronoUnit.valueOf(unit)
    val label = when (chronoUnit) {
        ChronoUnit.SECONDS -> if (amount == 1L) "second" else "seconds"
        ChronoUnit.MINUTES -> if (amount == 1L) "minute" else "minutes"
        ChronoUnit.HOURS   -> if (amount == 1L) "hour" else "hours"
        ChronoUnit.DAYS    -> if (amount == 1L) "day" else "days"
        ChronoUnit.WEEKS -> if (amount == 1L) "week" else "weeks"
        else -> unit.toString().lowercase()
    }
    return "$amount $label"
}