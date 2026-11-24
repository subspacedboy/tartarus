package club.subjugated.overlord_exe.convo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView

enum class ParamType(val jsonSchemaType: String) {
    STRING("string"),
    BOOLEAN("boolean"),
    INTEGER("integer"),
}

data class IntentParam(
    val name: String,
    val type: ParamType = ParamType.STRING,
    val description: String,
    val required: Boolean = true,
    val enumValues: List<String>? = null,
)

class MissingIntentParameterException(
    val intentName: String,
    val paramName: String,
    val paramDescription: String,
) : IllegalArgumentException("Missing required parameter '$paramName' ($paramDescription) for intent '$intentName'")

fun JsonElement.asStringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

fun JsonElement.asString(): String = asStringOrNull() ?: error("Expected string JSON primitive, got $this")

fun JsonElement.asBooleanOrNull(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

/**
 * Declarative specification of a conversational intent exposed as a native Gemini function declaration.
 */
data class IntentSpec<T : Any>(
    val name: String,
    val description: String,
    val parameters: List<IntentParam> = emptyList(),
    val examples: List<String> = emptyList(),
    val parser: (Map<String, JsonElement>) -> T,
    val handler: (ConversationContext, T) -> ConversationResponse,
) {
    fun fullDescription(): String = buildString {
        append(description)
        if (examples.isNotEmpty()) {
            append("\nExamples:\n")
            append(examples.joinToString("\n") { "- $it" })
        }
    }

    fun toJsonSchemaObject(): JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                parameters.forEach { param ->
                    put(
                        param.name,
                        buildJsonObject {
                            put("type", param.type.jsonSchemaType)
                            put("description", param.description)
                            if (!param.enumValues.isNullOrEmpty()) {
                                put("enum", JsonArray(param.enumValues.map { JsonPrimitive(it) }))
                            }
                        },
                    )
                }
            },
        )
        val requiredParams = parameters.filter { it.required }.map { JsonPrimitive(it.name) }
        if (requiredParams.isNotEmpty()) {
            put("required", JsonArray(requiredParams))
        }
    }

    fun parseCall(input: Map<String, JsonElement>): IntentCall<T> {
        for (param in parameters) {
            if (param.required) {
                val value = input[param.name]
                    ?: throw MissingIntentParameterException(name, param.name, param.description)
                if (param.type == ParamType.STRING && value.asStringOrNull().isNullOrBlank()) {
                    throw MissingIntentParameterException(name, param.name, param.description)
                }
            }
        }
        return IntentCall(this, parser(input))
    }
}

/**
 * Resolved, strongly-typed invocation of an [IntentSpec].
 */
data class IntentCall<T : Any>(
    val spec: IntentSpec<T>,
    val payload: T,
) {
    val name: String
        get() = spec.name

    fun execute(ctx: ConversationContext): ConversationResponse {
        return spec.handler(ctx, payload)
    }
}

/**
 * Helper for zero-argument intents (e.g. "timer", "superbot", "hello", "help").
 */
fun unitIntent(
    name: String,
    description: String,
    examples: List<String> = emptyList(),
    handler: (ConversationContext) -> ConversationResponse,
): IntentSpec<Unit> = IntentSpec(
    name = name,
    description = description,
    parameters = emptyList(),
    examples = examples,
    parser = { Unit },
    handler = { ctx, _ -> handler(ctx) },
)

/**
 * Helper for single-string-parameter intents (e.g. "add_list", "proxy_lock", "proxy_unlock", "proxy_release").
 */
fun stringIntent(
    name: String,
    paramName: String,
    paramDescription: String,
    description: String,
    examples: List<String> = emptyList(),
    enumValues: List<String>? = null,
    handler: (ConversationContext, String) -> ConversationResponse,
): IntentSpec<String> = IntentSpec(
    name = name,
    description = description,
    parameters = listOf(
        IntentParam(
            name = paramName,
            type = ParamType.STRING,
            description = paramDescription,
            required = true,
            enumValues = enumValues,
        )
    ),
    examples = examples,
    parser = { map -> map[paramName]?.asStringOrNull()?.trim() ?: "" },
    handler = handler,
)

/**
 * Helper for multi-parameter structured intents (e.g. "proxy_contract").
 */
fun <T : Any> intent(
    name: String,
    description: String,
    parameters: List<IntentParam>,
    examples: List<String> = emptyList(),
    parser: (Map<String, JsonElement>) -> T,
    handler: (ConversationContext, T) -> ConversationResponse,
): IntentSpec<T> = IntentSpec(
    name = name,
    description = description,
    parameters = parameters,
    examples = examples,
    parser = parser,
    handler = handler,
)

interface ConversationHandler {
    val commands: Set<String>
        get() = emptySet()

    val intents: List<IntentSpec<*>>
        get() = emptyList()

    fun handleCommand(ctx: ConversationContext, command: String, args: List<String>): String = ""

    fun handle(convoId: String, message: ConvoDefsMessageView): String {
        val messageMinusBang = message.text.removePrefix("!").trim()
        val chunks = if (messageMinusBang.isEmpty()) emptyList() else messageMinusBang.split("\\s+".toRegex())
        val cmd = chunks.firstOrNull()?.lowercase() ?: ""
        val args = if (chunks.size > 1) chunks.drop(1) else emptyList()
        val ctx = ConversationContext(userContext = null, convoId = convoId, currentMessage = message)
        return handleCommand(ctx, cmd, args)
    }
}
