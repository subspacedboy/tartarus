package club.subjugated.overlord_exe.bots.simple_proxy.convo

import club.subjugated.overlord_exe.services.ExplainsIntent
import club.subjugated.overlord_exe.services.Intent
import club.subjugated.overlord_exe.services.IntentExplanation
import club.subjugated.overlord_exe.util.explainFields
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ProxyIntakeData(
    val otherUser: String?,
    val public: Boolean?,
)

class ProxyContractIntent(
    val proxyIntakeData: ProxyIntakeData
) : Intent {

    companion object : ExplainsIntent<ProxyContractIntent, ProxyIntakeData> {
        override val dataClass = ProxyIntakeData::class

        val explanation = explainFields(ProxyIntakeData::class)
        val json = Json.encodeToString(explanation)

        override fun getExplanation() = IntentExplanation(
            intentName = "proxy_contract",
            explanation = "The user is trying to own, lock, or put another user under contract.",
            requiredInfo = json
        )

        override fun instantiate(data: ProxyIntakeData) = ProxyContractIntent(data)
    }
}