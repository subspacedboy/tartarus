package club.subjugated.overlord_exe.intents

import club.subjugated.overlord_exe.services.ExplainsIntent
import club.subjugated.overlord_exe.services.Intent
import club.subjugated.overlord_exe.services.IntentExplanation

data class SayHelloIntent(
    var placeHolder: String = ""
) : Intent {
    companion object : ExplainsIntent<SayHelloIntent, String> {
        override val dataClass = String::class

        val examples : List<String> = listOf(
            "Input: Hello. Output: Nothing.",
            "Input: Who are you?. Output: Nothing.",
        )

        override fun getExplanation() = IntentExplanation(
            intentName = "hello",
            explanation = "The user is saying hello or asking who you are.",
            requiredInfo = "None",
            examples = examples
        )

        override fun instantiate(data: String): SayHelloIntent {
            return SayHelloIntent(data)
        }
    }
}