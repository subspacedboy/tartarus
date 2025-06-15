package club.subjugated.overlord_exe.intents

import club.subjugated.overlord_exe.services.ExplainsIntent
import club.subjugated.overlord_exe.services.Intent
import club.subjugated.overlord_exe.services.IntentExplanation
import club.subjugated.overlord_exe.util.explainFields
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RemoveFromListData(
    val listName: String?
)

class RemoveFromListIntent(
    val removeFromListData: RemoveFromListData
) : Intent {
    companion object : ExplainsIntent<RemoveFromListIntent, RemoveFromListData> {
        override val dataClass = RemoveFromListData::class

        val explanation = explainFields(RemoveFromListData::class)
        val json = Json.encodeToString(explanation)

        val examples : List<String> = listOf(
            "Input: I want to be removed from a list. Output: Need to know list name.",
            "Input: I want to be removed from subs. Output: ${Json.encodeToString(RemoveFromListData(
                listName = "subs"
            ))}.",
        )

        override fun getExplanation() = IntentExplanation(
            intentName = "remove_list",
            explanation = "The user is wants to be removed from a Bluesky list. The lists are: 'Doms', 'Subs', 'Public Property'.",
            requiredInfo = json,
            examples = examples
        )

        override fun instantiate(data: RemoveFromListData) = RemoveFromListIntent(data)
    }
}