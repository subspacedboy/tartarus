package club.subjugated.overlord_exe.intents

import club.subjugated.overlord_exe.services.ExplainsIntent
import club.subjugated.overlord_exe.services.Intent
import club.subjugated.overlord_exe.services.IntentExplanation
import club.subjugated.overlord_exe.util.explainFields
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AddToListData(
    val listName: String?
)

class AddToListIntent(
    val addToListData: AddToListData
) : Intent {

    companion object : ExplainsIntent<AddToListIntent, AddToListData> {
        override val dataClass = AddToListData::class

        val explanation = explainFields(AddToListData::class)
        val json = Json.encodeToString(explanation)

        val examples : List<String> = listOf(
            "Input: I want to be added to a list. Output: Need to know list name.",
            "Input: I want to be added to subs. Output: ${Json.encodeToString(AddToListData(
                listName = "subs"
            ))}.",
        )

        override fun getExplanation() = IntentExplanation(
            intentName = "add_list",
            explanation = "The user is wants to be added to a Bluesky list. The lists are: 'Doms', 'Subs', 'Public Property'.",
            requiredInfo = json,
            examples = examples
        )

        override fun instantiate(data: AddToListData) = AddToListIntent(data)
    }
}