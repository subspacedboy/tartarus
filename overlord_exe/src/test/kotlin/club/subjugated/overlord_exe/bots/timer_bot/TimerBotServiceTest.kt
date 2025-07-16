package club.subjugated.overlord_exe.bots.timer_bot

import java.time.Duration
import java.time.temporal.ChronoUnit
import kotlin.random.Random
import kotlin.test.Test

class TimerBotServiceTest {
    @Test
    fun testRandomDuration() {
        val minDuration = Duration.of(0, ChronoUnit.valueOf("MINUTES"))

        val maxAmount = 5
        val maxUnit = ChronoUnit.valueOf("HOURS")
        val maxDuration = Duration.of(maxAmount.toLong(), maxUnit)

        val minMinutes = minDuration.toMinutes()
        val maxMinutes = maxDuration.toMinutes()

        val randomMinutes = Random.nextLong(minMinutes, maxMinutes + 1)
        println(randomMinutes)
        val d = Duration.ofMinutes(randomMinutes)
        println(d)
    }
}