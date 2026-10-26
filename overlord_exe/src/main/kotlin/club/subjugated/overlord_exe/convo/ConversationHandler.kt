package club.subjugated.overlord_exe.convo

import aws.sdk.kotlin.services.bedrockruntime.model.ToolInputSchema
import aws.sdk.kotlin.services.bedrockruntime.model.ToolSpecification
import aws.smithy.kotlin.runtime.content.Document
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
)

class MissingIntentParameterException(
    val intentName: String,
    val paramName: String,
    val paramDescription: String,
) : IllegalArgumentException("Missing required parameter '$paramName' ($paramDescription) for intent '$intentName'")

/**
 * Declarative specification of a conversational intent exposed as a native Bedrock Converse tool.
 */
data class IntentSpec<T : Any>(
    val name: String,
    val description: String,
    val parameters: List<IntentParam> = emptyList(),
    val examples: List<String> = emptyList(),
    val parser: (Map<String, Document>) -> T,
    val handler: (ConversationContext, T) -> ConversationResponse,
) {
    fun toToolSpecification(): ToolSpecification {
        val fullDescription = buildString {
            append(description)
            if (examples.isNotEmpty()) {
                append("\nExamples:\n")
                append(examples.joinToString("\n") { "- $it" })
            }
        }
        return ToolSpecification {
            this.name = this@IntentSpec.name
            this.description = fullDescription
            this.inputSchema = ToolInputSchema.Json(toJsonSchemaDocument())
        }
    }

    fun toJsonSchemaDocument(): Document {
        val properties = parameters.associate { param ->
            param.name to Document(
                mapOf(
                    "type" to Document(param.type.jsonSchemaType),
                    "description" to Document(param.description),
                )
            )
        }
        val requiredList = parameters
            .filter { it.required }
            .map { Document(it.name) }

        return Document(
            mapOf(
                "type" to Document("object"),
                "properties" to Document(properties),
                "required" to Document(requiredList),
            )
        )
    }

    fun parseCall(input: Map<String, Document>): IntentCall<T> {
        for (param in parameters) {
            if (param.required) {
                val doc = input[param.name]
                    ?: throw MissingIntentParameterException(name, param.name, param.description)
                if (param.type == ParamType.STRING && doc.asStringOrNull().isNullOrBlank()) {
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
 * Helper for zero-argument intents (e.g. "timer", "superbot", "hello").
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
    parser: (Map<String, Document>) -> T,
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
    /**
     * Lowercase literal "!" command names routed to this handler (e.g. "timer", "superbot", "unlock").
     */
    val commands: Set<String>
        get() = emptySet()

    /**
     * Declarative LLM tool/intent specifications exposed to Bedrock Converse ToolConfiguration.
     */
    val intents: List<IntentSpec<*>>
        get() = emptyList()

    /**
     * Handles a literal `!command` DM with a pre-populated [ConversationContext] and parsed arguments.
     */
    fun handleCommand(ctx: ConversationContext, command: String, args: List<String>): String = ""

    /**
     * Convenience wrapper for callers that pass raw `(convoId, message)`.
     */
    fun handle(convoId: String, message: ConvoDefsMessageView): String {
        val messageMinusBang = message.text.removePrefix("!").trim()
        val chunks = if (messageMinusBang.isEmpty()) emptyList() else messageMinusBang.split("\\s+".toRegex())
        val cmd = chunks.firstOrNull()?.lowercase() ?: ""
        val args = if (chunks.size > 1) chunks.drop(1) else emptyList()
        val ctx = ConversationContext(bskyUser = null, convoId = convoId, currentMessage = message)
        return handleCommand(ctx, cmd, args)
    }
}
