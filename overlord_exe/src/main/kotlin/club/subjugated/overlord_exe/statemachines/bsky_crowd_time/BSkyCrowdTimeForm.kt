package club.subjugated.overlord_exe.statemachines.bsky_crowd_time

import club.subjugated.overlord_exe.statemachines.ContextForm
import java.time.DateTimeException
import java.time.Duration
import java.time.temporal.ChronoUnit

class BSkyCrowdTimeForm(
    var perLikeAdd: Long = 1,
    var perLikeAddUnit: String = "MINUTES",
    var perRepostAdd: Long = 1,
    var perRepostAddUnit: String = "HOURS",
    var openPeriodAmount: Long = 1,
    var openPeriodUnit: String = "HOURS",
    var subjectDid: String? = "",
    override var name: String = "",
) : ContextForm {
    override fun validate(): List<String> {
        val result = mutableListOf<String>()

        try {
            val perLikeUnit = ChronoUnit.valueOf(perLikeAddUnit)
            val perLikeDuration = Duration.of(perLikeAdd, perLikeUnit)
        } catch (e : DateTimeException) {
            result.add("Per like amount of unit are invalid: $e")
        } catch (e : ArithmeticException) {
            result.add("Per like amount of unit are invalid: $e")
        }

        try {
            val perRepostUnit = ChronoUnit.valueOf(perRepostAddUnit)
            val perRepostDuration = Duration.of(perRepostAdd, perRepostUnit)
        } catch (e : DateTimeException) {
            result.add("Per repost amount of unit are invalid: $e")
        } catch (e : ArithmeticException) {
            result.add("Per repost amount of unit are invalid: $e")
        }

        try {
            val openAmountUnit = ChronoUnit.valueOf(openPeriodUnit)
            val openPeriodDuration = Duration.of(openPeriodAmount, openAmountUnit)
        } catch (e : DateTimeException) {
            result.add("Open period amount of unit are invalid: $e")
        } catch (e : ArithmeticException) {
            result.add("Open period amount of unit are invalid: $e")
        }
        return result
    }
}