package club.subjugated.overlord_exe.bots.simple_proxy.convo

import club.subjugated.overlord_exe.services.ExplainsIntent
import club.subjugated.overlord_exe.services.Intent
import club.subjugated.overlord_exe.services.IntentExplanation
import club.subjugated.overlord_exe.util.explainFields
import kotlinx.serialization.json.Json

class ProxyReleaseIntent(
    val proxyData : ProxyData
) : Intent {

    companion object : ExplainsIntent<ProxyReleaseIntent, ProxyData> {
        override val dataClass = ProxyData::class

        val explanation = explainFields(ProxyData::class)
        val json = Json.encodeToString(explanation)

        val examples : List<String> = listOf(
            "Input: I want to release another user from contract. Output: Need to know user.",
            "Input: I want to release @subspacedboy.subjugated.club. Output: ${Json.encodeToString(ProxyData(
                otherUserHandle = "@subspacedboy.subjugated.club",
            ))}.",
        )

        override fun getExplanation() = IntentExplanation(
            intentName = "proxy_release",
            explanation = "The user is trying to release another user from contract.",
            requiredInfo = json,
            examples = examples
        )

        override fun instantiate(data: ProxyData) = ProxyReleaseIntent(data)
    }
}