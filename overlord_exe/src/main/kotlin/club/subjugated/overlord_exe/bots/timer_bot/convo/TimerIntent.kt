package club.subjugated.overlord_exe.bots.timer_bot.convo

import club.subjugated.overlord_exe.services.ExplainsIntent
import club.subjugated.overlord_exe.services.Intent
import club.subjugated.overlord_exe.services.IntentExplanation


data class Timer(
    var placeHolder : String
) : Intent {

    companion object : ExplainsIntent<Timer, String> {
        override val dataClass = String::class

        val examples : List<String> = listOf(
            "Input: I want to self lock. Output: Nothing.",
            "Input: Lock me for a few hours. Output: Nothing.",
            "Input: Lock me up for a few hours daddy. Output: Nothing."
        )

        override fun getExplanation() = IntentExplanation(
            intentName = "timer",
            explanation = "The user is trying to self-lock for a period of time. If you believe this is the intent, no follow up questions are necessary.",
            requiredInfo = "None",
            examples = examples
        )

        override fun instantiate(data: String): Timer {
            return Timer("")
        }
    }
}