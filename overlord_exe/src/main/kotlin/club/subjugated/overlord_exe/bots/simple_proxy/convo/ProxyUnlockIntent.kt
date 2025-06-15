package club.subjugated.overlord_exe.bots.simple_proxy.convo

import club.subjugated.overlord_exe.services.ExplainsIntent
import club.subjugated.overlord_exe.services.Intent
import club.subjugated.overlord_exe.services.IntentExplanation
import club.subjugated.overlord_exe.util.explainFields
import kotlinx.serialization.json.Json


class ProxyUnlockIntent(
    val proxyData : ProxyData
) : Intent {

    companion object : ExplainsIntent<ProxyUnlockIntent, ProxyData> {
        override val dataClass = ProxyData::class

        val explanation = explainFields(ProxyData::class)
        val json = Json.encodeToString(explanation)

        val examples : List<String> = listOf(
            "Input: I want to unlock another user. Output: Need to know user.",
            "Input: I want to unlock @subspacedboy.subjugated.club. Output: ${Json.encodeToString(ProxyData(
                otherUserHandle = "@subspacedboy.subjugated.club",
            ))}.",
        )

        override fun getExplanation() = IntentExplanation(
            intentName = "proxy_unlock",
            explanation = "The user is trying to unlock another user.",
            requiredInfo = json,
            examples = examples
        )

        override fun instantiate(data: ProxyData) = ProxyUnlockIntent(data)
    }
}