package club.subjugated.overlord_exe.bots.simple_proxy.convo

import club.subjugated.overlord_exe.services.ExplainsIntent
import club.subjugated.overlord_exe.services.Intent
import club.subjugated.overlord_exe.services.IntentExplanation
import club.subjugated.overlord_exe.util.explainFields
import kotlinx.serialization.json.Json

class ProxyLockIntent(
    val proxyData : ProxyData
) : Intent {

    companion object : ExplainsIntent<ProxyLockIntent, ProxyData> {
        override val dataClass = ProxyData::class

        val explanation = explainFields(ProxyData::class)
        val json = Json.encodeToString(explanation)

        val examples : List<String> = listOf(
            "Input: Lock @subspacedboy.subjugated.club. Output: ${Json.encodeToString(ProxyData(
                otherUserHandle = "@subspacedboy.subjugated.club",
            ))}.",
        )

        override fun getExplanation() = IntentExplanation(
            intentName = "proxy_lock",
            explanation = "The user is trying to relock another user that's already under contract. If you're not sure ask if this is a new contract. New contract should use proxy_contract.",
            requiredInfo = json,
            examples = examples
        )

        override fun instantiate(data: ProxyData) = ProxyLockIntent(data)
    }
}