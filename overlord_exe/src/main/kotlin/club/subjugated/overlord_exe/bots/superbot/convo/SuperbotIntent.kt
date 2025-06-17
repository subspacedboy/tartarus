package club.subjugated.overlord_exe.bots.superbot.convo

import club.subjugated.overlord_exe.bots.timer_bot.convo.Timer
import club.subjugated.overlord_exe.services.ExplainsIntent
import club.subjugated.overlord_exe.services.Intent
import club.subjugated.overlord_exe.services.IntentExplanation

class SuperbotIntent(
    var placeholder : String? = ""
) : Intent {
    companion object : ExplainsIntent<SuperbotIntent, String> {
        override val dataClass = String::class

        val examples : List<String> = listOf(
            "Input: Can you own me?. Output: Nothing.",
            "Input: Lock me for tasks on Bluesky. Output: Nothing.",
            "Input: Can you lock me and make me do some tasks?. Output: Nothing."
        )

        override fun getExplanation() = IntentExplanation(
            intentName = "superbot",
            explanation = "The user is wants to be owned, controlled, or locked by you (Overlord-exe). If you believe this is the intent, no follow up questions are necessary.",
            requiredInfo = "None",
            examples = examples
        )

        override fun instantiate(data: String): SuperbotIntent {
            return SuperbotIntent("")
        }
    }
}