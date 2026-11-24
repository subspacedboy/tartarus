package club.subjugated.overlord_exe.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Serializable
data class GeminiFunctionCall(
    val name: String,
    val args: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val functionCall: GeminiFunctionCall? = null,
)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

@Serializable
data class GeminiFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

@Serializable
data class GeminiTool(
    val functionDeclarations: List<GeminiFunctionDeclaration>,
)

@Serializable
data class GeminiThinkingConfig(
    val thinkingLevel: String = "LOW",
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Float = 0.0f,
    val maxOutputTokens: Int = 300,
    val thinkingConfig: GeminiThinkingConfig? = null,
)

@Serializable
data class GeminiGenerateContentRequest(
    val systemInstruction: GeminiContent? = null,
    val contents: List<GeminiContent>,
    val tools: List<GeminiTool>? = null,
    val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
)

@Serializable
data class GeminiUsageMetadata(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
    val totalTokenCount: Int? = null,
)

@Serializable
data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val usageMetadata: GeminiUsageMetadata? = null,
)

interface GeminiClient {
    fun generateContent(
        model: String,
        request: GeminiGenerateContentRequest,
    ): GeminiGenerateContentResponse
}

@Service
class KtorGeminiClient(
    @Value("\${overlord.gemini.api-key:}") private val configuredApiKey: String = "",
    @Value("\${overlord.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") private val baseUrl: String =
        "https://generativelanguage.googleapis.com/v1beta",
) : GeminiClient {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(jsonParser)
        }
    }

    @PreDestroy
    fun close() {
        runCatching { httpClient.close() }
    }

    private fun resolveApiKey(): String {
        val key = configuredApiKey.trim().ifBlank { System.getenv("GEMINI_API_KEY")?.trim().orEmpty() }
        require(key.isNotBlank()) { "Gemini API key is not configured (set overlord.gemini.api-key or GEMINI_API_KEY)" }
        return key
    }

    override fun generateContent(
        model: String,
        request: GeminiGenerateContentRequest,
    ): GeminiGenerateContentResponse {
        val apiKey = resolveApiKey()
        val trimmedBase = baseUrl.trim().trimEnd('/')
        return runBlocking(Dispatchers.IO) {
            val response = httpClient.post("$trimmedBase/models/$model:generateContent") {
                header("x-goog-api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException(
                    "Gemini generateContent failed for $model: HTTP ${response.status} - ${response.bodyAsText()}"
                )
            }
            response.body()
        }
    }
}
