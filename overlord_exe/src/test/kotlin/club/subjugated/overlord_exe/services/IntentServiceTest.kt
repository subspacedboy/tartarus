package club.subjugated.overlord_exe.services

import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.convo.ConversationHandler
import club.subjugated.overlord_exe.convo.ConversationResponse
import club.subjugated.overlord_exe.convo.IntentSpec
import club.subjugated.overlord_exe.convo.stringIntent
import club.subjugated.overlord_exe.convo.unitIntent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView

class TestConversationHandler : ConversationHandler {
    override val intents: List<IntentSpec<*>> = listOf(
        unitIntent(
            name = "hello",
            description = "The user is saying hello or asking who you are.",
            examples = listOf("Hello", "Who are you?"),
        ) {
            ConversationResponse("Hello 😈! I'm Overlord-exe.")
        },
        stringIntent(
            name = "add_list",
            paramName = "listName",
            paramDescription = "Name of the Bluesky list to join ('Doms', 'Subs', or 'Public Property')",
            description = "The user wants to be added to a Bluesky list.",
            enumValues = listOf("Doms", "Subs", "Public Property"),
        ) { _, listName ->
            ConversationResponse("Added to $listName")
        },
        stringIntent(
            name = "remove_list",
            paramName = "listName",
            paramDescription = "Name of the Bluesky list to leave ('Doms', 'Subs', or 'Public Property')",
            description = "The user wants to be removed from a Bluesky list.",
            enumValues = listOf("Doms", "Subs", "Public Property"),
        ) { _, listName ->
            ConversationResponse("Removed from $listName")
        },
    )
}

class IntentServiceTest {

    private lateinit var rateLimiter: IntentRateLimiter
    private lateinit var mockGemini: GeminiClient
    private lateinit var intentService: IntentService

    @BeforeEach
    fun setUp() {
        rateLimiter = IntentRateLimiter(
            maxMessageLength = 100,
            convoRateLimitCount = 5,
            convoRateLimitWindowSeconds = 30,
            convoMinIntervalSeconds = 0,
            globalRateLimitCount = 20,
            globalRateLimitWindowSeconds = 60,
        )
        mockGemini = mock()
        intentService = IntentService(
            intentProviders = listOf(TestConversationHandler()),
            rateLimiter = rateLimiter,
            geminiClient = mockGemini,
        )
    }

    @Test
    fun `buildTools registers all declarative IntentSpecs with JSON Schema and enums`() {
        val tools = intentService.buildTools()
        assertNotNull(tools)
        val declarations = tools!!.first().functionDeclarations
        assertEquals(listOf("hello", "add_list", "remove_list"), declarations.map { it.name })

        val addList = declarations.first { it.name == "add_list" }
        assertEquals("object", addList.parameters["type"]?.jsonPrimitive?.content)
        val required = addList.parameters["required"]?.jsonArray?.map { it.jsonPrimitive.content }
        assertEquals(listOf("listName"), required)
        val enumVals = addList.parameters["properties"]?.jsonObject?.get("listName")
            ?.jsonObject?.get("enum")?.jsonArray?.map { it.jsonPrimitive.content }
        assertEquals(listOf("Doms", "Subs", "Public Property"), enumVals)
    }

    @Test
    fun `resolve returns rejection when message exceeds size limit without calling Gemini`() {
        val hugeMessage = "x".repeat(101)
        val resolved = intentService.resolve("convo-1", hugeMessage)

        assertNull(resolved.intent)
        assertNotNull(resolved.chat)
        assertTrue(resolved.chat!!.contains("too long"))
    }

    @Test
    fun `resolve returns rejection when message is blank`() {
        val resolved = intentService.resolve("convo-1", "   ")

        assertNull(resolved.intent)
        assertNotNull(resolved.chat)
        assertEquals("Please send a valid message.", resolved.chat)
    }

    @Test
    fun `resolve resets and warns when conversation exceeds max conversation turns`() {
        intentService.maxConversationTurns = 3

        val convoId = "convo-loop"
        for (i in 1..3) {
            intentService.addMessageToContext(
                convoId,
                GeminiContent(role = "user", parts = listOf(GeminiPart(text = "User turn $i"))),
            )
            intentService.addMessageToContext(
                convoId,
                GeminiContent(role = "model", parts = listOf(GeminiPart(text = "Model turn $i"))),
            )
        }

        val resolved = intentService.resolve(convoId, "Yet another question")
        assertNull(resolved.intent)
        assertTrue(resolved.chat!!.contains("talking for a while without completing"))
        assertEquals(0, intentService.getContext(convoId).size)
    }

    @Test
    fun `getTrimmedContext keeps bounded messages, merges consecutive turns, and starts with user`() {
        intentService.maxContextMessages = 4
        val convoId = "trim-test"

        for (i in 1..3) {
            intentService.addMessageToContext(
                convoId,
                GeminiContent(role = "user", parts = listOf(GeminiPart(text = "U$i"))),
            )
            intentService.addMessageToContext(
                convoId,
                GeminiContent(role = "model", parts = listOf(GeminiPart(text = "A$i"))),
            )
        }

        assertEquals(6, intentService.getContext(convoId).size)

        val trimmed = intentService.getTrimmedContext(convoId)
        assertEquals(4, trimmed.size)
        assertEquals("user", trimmed.first().role)
        assertEquals("U2", trimmed.first().parts.first().text)

        intentService.maxContextMessages = 3
        val trimmedOdd = intentService.getTrimmedContext(convoId)
        assertEquals(2, trimmedOdd.size)
        assertEquals("user", trimmedOdd.first().role)
        assertEquals("U3", trimmedOdd.first().parts.first().text)
    }

    @Test
    fun `reifyToolUse reconstructs strongly typed IntentCalls and executes handler`() {
        val addListCall = intentService.reifyToolUse(
            "add_list",
            JsonObject(mapOf("listName" to JsonPrimitive("Doms"))),
        )
        assertEquals("add_list", addListCall.name)
        assertEquals("Doms", addListCall.payload)

        val dummyCtx = ConversationContext(
            userContext = null,
            convoId = "c-1",
            currentMessage = mock<ConvoDefsMessageView>(),
        )
        assertEquals("Added to Doms", intentService.dispatch(dummyCtx, addListCall).text)

        val helloCall = intentService.reifyToolUse("hello", JsonObject(emptyMap()))
        assertEquals("hello", helloCall.name)
        assertEquals(Unit, helloCall.payload)
    }

    @Test
    fun `resolve handles native Gemini functionCall and clears context`() {
        val fakeResponse = GeminiGenerateContentResponse(
            candidates = listOf(
                GeminiCandidate(
                    content = GeminiContent(
                        role = "model",
                        parts = listOf(
                            GeminiPart(
                                functionCall = GeminiFunctionCall(
                                    name = "hello",
                                    args = JsonObject(emptyMap()),
                                )
                            )
                        ),
                    )
                )
            ),
            usageMetadata = GeminiUsageMetadata(promptTokenCount = 120, candidatesTokenCount = 10),
        )
        whenever(mockGemini.generateContent(any(), any())).thenReturn(fakeResponse)

        val result = intentService.resolve("c-123", "Hello bot")
        assertNotNull(result.intent)
        assertEquals("hello", result.intent!!.name)
        assertEquals("", result.chat)
        assertEquals(0, intentService.getContext("c-123").size)
    }

    @Test
    fun `resolve handles Gemini follow up text chat and retains context`() {
        val fakeResponse = GeminiGenerateContentResponse(
            candidates = listOf(
                GeminiCandidate(
                    content = GeminiContent(
                        role = "model",
                        parts = listOf(GeminiPart(text = "Which list would you like to join?")),
                    )
                )
            )
        )
        whenever(mockGemini.generateContent(any(), any())).thenReturn(fakeResponse)

        val result = intentService.resolve("c-456", "I want to join a list")
        assertNull(result.intent)
        assertEquals("Which list would you like to join?", result.chat)
        assertEquals(2, intentService.getContext("c-456").size)
    }

    @Test
    fun `resolve asks user for clarification when functionCall omits required parameter`() {
        val fakeResponse = GeminiGenerateContentResponse(
            candidates = listOf(
                GeminiCandidate(
                    content = GeminiContent(
                        role = "model",
                        parts = listOf(
                            GeminiPart(
                                functionCall = GeminiFunctionCall(
                                    name = "add_list",
                                    args = JsonObject(emptyMap()),
                                )
                            )
                        ),
                    )
                )
            )
        )
        whenever(mockGemini.generateContent(any(), any())).thenReturn(fakeResponse)

        val result = intentService.resolve("c-789", "Add me to a list")
        assertNull(result.intent)
        assertTrue(result.chat!!.contains("Name of the Bluesky list to join"))
        assertEquals(2, intentService.getContext("c-789").size)
    }

    @Test
    fun `resolve strips thinking tags from assistant text and falls back if only thinking was emitted`() {
        val mixedResponse = GeminiGenerateContentResponse(
            candidates = listOf(
                GeminiCandidate(
                    content = GeminiContent(
                        role = "model",
                        parts = listOf(
                            GeminiPart(
                                text = "<thinking>Need list name.</thinking>\nWhich list would you like to join?"
                            )
                        ),
                    )
                )
            )
        )
        whenever(mockGemini.generateContent(any(), any())).thenReturn(mixedResponse)

        val result1 = intentService.resolve("c-think-1", "Add me to a list")
        assertNull(result1.intent)
        assertEquals("Which list would you like to join?", result1.chat)

        val thinkingOnlyResponse = GeminiGenerateContentResponse(
            candidates = listOf(
                GeminiCandidate(
                    content = GeminiContent(
                        role = "model",
                        parts = listOf(
                            GeminiPart(text = "<thinking>Internal thought only.</thinking>")
                        ),
                    )
                )
            )
        )
        whenever(mockGemini.generateContent(any(), any())).thenReturn(thinkingOnlyResponse)

        val result2 = intentService.resolve("c-think-2", "help")
        assertNull(result2.intent)
        assertTrue(result2.chat!!.contains("What would you like me to help you with?"))
    }
}
