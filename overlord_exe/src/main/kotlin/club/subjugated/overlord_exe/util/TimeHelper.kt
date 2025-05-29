package club.subjugated.overlord_exe.util

import java.time.temporal.ChronoUnit

fun formatDuration(amount: Int, unit: String): String {
    val chronoUnit = ChronoUnit.valueOf(unit)
    val label = when (chronoUnit) {
        ChronoUnit.SECONDS -> if (amount == 1) "second" else "seconds"
        ChronoUnit.MINUTES -> if (amount == 1) "minute" else "minutes"
        ChronoUnit.HOURS   -> if (amount == 1) "hour" else "hours"
        ChronoUnit.DAYS    -> if (amount == 1) "day" else "days"
        ChronoUnit.WEEKS -> if (amount == 1) "week" else "weeks"
        else -> unit.toString().lowercase()
    }
    return "$amount $label"
}