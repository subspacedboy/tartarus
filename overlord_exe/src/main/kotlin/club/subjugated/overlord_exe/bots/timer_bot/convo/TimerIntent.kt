package club.subjugated.overlord_exe.bots.timer_bot.convo

import club.subjugated.overlord_exe.bots.simple_proxy.convo.ProxyIntakeData
import club.subjugated.overlord_exe.services.ExplainsIntent
import club.subjugated.overlord_exe.services.Intent
import club.subjugated.overlord_exe.services.IntentExplanation
import kotlin.reflect.KClass


data class Timer(
    var placeHolder : String
) : Intent {

    companion object : ExplainsIntent<Timer, String> {
        override val dataClass = String::class

        override fun getExplanation() = IntentExplanation(
            intentName = "timer",
            explanation = "The user is trying to self-lock for a period of time.",
            requiredInfo = "None"
        )

        override fun instantiate(data: String): Timer {
            return Timer("")
        }
    }
}