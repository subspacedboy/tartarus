package club.subjugated.overlord_exe.services

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.ContentBlock
import aws.sdk.kotlin.services.bedrockruntime.model.ConversationRole
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseOutput
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseResponse
import aws.sdk.kotlin.services.bedrockruntime.model.Message
import aws.sdk.kotlin.services.bedrockruntime.model.StopReason
import kotlinx.coroutines.runBlocking
import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.convo.ConversationHandler
import club.subjugated.overlord_exe.convo.ConversationResponse
import club.subjugated.overlord_exe.intents.AddToListData
import club.subjugated.overlord_exe.intents.AddToListIntent
import club.subjugated.overlord_exe.intents.RemoveFromListIntent
import club.subjugated.overlord_exe.intents.SayHelloIntent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import kotlin.reflect.KClass

class TestConversationHandler : ConversationHandler {
    override fun handle(convoId: String, message: ConvoDefsMessageView): String = ""
    override fun getIntents(): List<KClass<out Intent>> = listOf(
        AddToListIntent::class,
        RemoveFromListIntent::class,
        SayHelloIntent::class
    )
    override fun handleIntent(ctx: ConversationContext, intent: Intent): ConversationResponse =
        ConversationResponse("handled")
}

class IntentServiceTest {

    private lateinit var rateLimiter: IntentRateLimiter
    private lateinit var intentService: IntentService

    @BeforeEach
    fun setUp() {
        rateLimiter = IntentRateLimiter(
            maxMessageLength = 100,
            convoRateLimitCount = 5,
            convoRateLimitWindowSeconds = 30,
            convoMinIntervalSeconds = 0, // 0 for simpler unit tests
            globalRateLimitCount = 20,
            globalRateLimitWindowSeconds = 60
        )

        intentService = IntentService(
            intentProviders = listOf(TestConversationHandler()),
            rateLimiter = rateLimiter
        )
    }

    @Test
    fun `extractJson extracts raw json cleanly`() {
        val input = """{"intent":"hello","data":"","chat":""}"""
        val extracted = intentService.extractJson(input)
        assertEquals(input, extracted)
    }

    @Test
    fun `extractJson removes markdown code blocks`() {
        val input = """
            ```json
            {
               "intent": "add_list",
               "data": "{\"listName\":\"subs\"}",
               "chat": ""
            }
            ```
        """.trimIndent()
        val extracted = intentService.extractJson(input)
        assertTrue(extracted.startsWith("{"))
        assertTrue(extracted.endsWith("}"))
        assertTrue(extracted.contains("\"intent\": \"add_list\""))
    }

    @Test
    fun `extractJson extracts json from surrounded explanatory text`() {
        val input = """
            Here is the detected intent:
            {"intent": "hello", "data": "", "chat": ""}
            I hope that helps!
        """.trimIndent()
        val extracted = intentService.extractJson(input)
        assertEquals("""{"intent": "hello", "data": "", "chat": ""}""", extracted)
    }

    @Test
    fun `resolve returns rejection when message exceeds size limit without calling bedrock`() {
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

        // Simulate 3 prior user messages in context
        val convoId = "convo-loop"
        for (i in 1..3) {
            intentService.addMessageToContext(convoId, Message {
                role = ConversationRole.User
                content = listOf(ContentBlock.Text("User turn $i"))
            })
            intentService.addMessageToContext(convoId, Message {
                role = ConversationRole.Assistant
                content = listOf(ContentBlock.Text("Assistant turn $i"))
            })
        }

        // 4th turn attempt
        val resolved = intentService.resolve(convoId, "Yet another question")
        assertNull(resolved.intent)
        assertTrue(resolved.chat!!.contains("talking for a while without completing"))

        // Context should be cleared
        assertEquals(0, intentService.getContext(convoId).size)
    }

    @Test
    fun `getTrimmedContext keeps bounded messages and always starts with User role`() {
        intentService.maxContextMessages = 4
        val convoId = "trim-test"

        // Add 6 alternating messages (3 turns)
        for (i in 1..3) {
            intentService.addMessageToContext(convoId, Message {
                role = ConversationRole.User
                content = listOf(ContentBlock.Text("U$i"))
            })
            intentService.addMessageToContext(convoId, Message {
                role = ConversationRole.Assistant
                content = listOf(ContentBlock.Text("A$i"))
            })
        }

        // Full context has 6 messages: [U1, A1, U2, A2, U3, A3]
        assertEquals(6, intentService.getContext(convoId).size)

        // Trimmed with maxContextMessages=4:
        // takeLast(4) gives [U2, A2, U3, A3]. It starts with User!
        val trimmed = intentService.getTrimmedContext(convoId)
        assertEquals(4, trimmed.size)
        assertEquals(ConversationRole.User, trimmed.first().role)
        assertEquals("U2", trimmed.first().content.first().asText())

        // Now test when takeLast starts with Assistant:
        intentService.maxContextMessages = 3
        // takeLast(3) gives [A2, U3, A3] -> first is Assistant!
        // Trimmer must drop A2 so that first message has role User
        val trimmedOdd = intentService.getTrimmedContext(convoId)
        assertEquals(2, trimmedOdd.size)
        assertEquals(ConversationRole.User, trimmedOdd.first().role)
        assertEquals("U3", trimmedOdd.first().content.first().asText())
    }

    @Test
    fun `system prompt contains Nova Lite raw json instruction and intent explanations`() {
        val prompt = intentService.makeSystemPrompt()

        assertTrue(prompt.contains("Overlord-exe"))
        assertTrue(prompt.contains("CRITICAL: Respond with ONLY a single raw JSON object"))
        assertTrue(prompt.contains("Name: hello"))
        assertTrue(prompt.contains("Name: add_list"))
        assertTrue(prompt.contains("Name: remove_list"))
    }

    @Test
    fun `reifyIntent reconstructs intent objects from json`() {
        // Build prompt first to register intents
        intentService.makeSystemPrompt()

        val addListIntent = intentService.reifyIntent("add_list", """{"listName":"Doms"}""")
        assertTrue(addListIntent is AddToListIntent)
        assertEquals("Doms", (addListIntent as AddToListIntent).addToListData.listName)

        val helloIntent = intentService.reifyIntent("hello", "")
        assertTrue(helloIntent is SayHelloIntent)
    }

    @Test
    fun `resolve handles successful Nova Lite intent resolution and clears context`() = runBlocking {
        val mockBedrock = org.mockito.kotlin.mock<BedrockRuntimeClient>()
        val serviceWithMock = IntentService(
            intentProviders = listOf(TestConversationHandler()),
            rateLimiter = rateLimiter,
            injectedBedrockClient = mockBedrock
        )
        serviceWithMock.makeSystemPrompt()

        val fakeBedrockResponse = ConverseResponse {
            output = ConverseOutput.Message(Message {
                role = ConversationRole.Assistant
                content = listOf(ContentBlock.Text("""{"intent":"hello","data":"","chat":""}"""))
            })
            stopReason = StopReason.EndTurn
        }

        org.mockito.kotlin.whenever(mockBedrock.converse(org.mockito.kotlin.any())).thenReturn(fakeBedrockResponse)

        val result = serviceWithMock.resolve("c-123", "Hello bot")
        assertNotNull(result.intent)
        assertTrue(result.intent is SayHelloIntent)
        assertEquals("", result.chat)
        // Context should be cleared after intent is resolved
        assertEquals(0, serviceWithMock.getContext("c-123").size)
    }

    @Test
    fun `resolve handles Nova Lite follow up chat and retains context`() = runBlocking {
        val mockBedrock = org.mockito.kotlin.mock<BedrockRuntimeClient>()
        val serviceWithMock = IntentService(
            intentProviders = listOf(TestConversationHandler()),
            rateLimiter = rateLimiter,
            injectedBedrockClient = mockBedrock
        )
        serviceWithMock.makeSystemPrompt()

        val fakeBedrockResponse = ConverseResponse {
            output = ConverseOutput.Message(Message {
                role = ConversationRole.Assistant
                content = listOf(ContentBlock.Text("""{"intent":"","data":"","chat":"Which list would you like to join?"}"""))
            })
            stopReason = StopReason.EndTurn
        }

        org.mockito.kotlin.whenever(mockBedrock.converse(org.mockito.kotlin.any())).thenReturn(fakeBedrockResponse)

        val result = serviceWithMock.resolve("c-456", "I want to join a list")
        assertNull(result.intent)
        assertEquals("Which list would you like to join?", result.chat)
        // Context should retain user and assistant message (2 messages)
        assertEquals(2, serviceWithMock.getContext("c-456").size)
    }

    @Test
    fun `resolve handles markdown wrapped Nova Lite response`() = runBlocking {
        val mockBedrock = org.mockito.kotlin.mock<BedrockRuntimeClient>()
        val serviceWithMock = IntentService(
            intentProviders = listOf(TestConversationHandler()),
            rateLimiter = rateLimiter,
            injectedBedrockClient = mockBedrock
        )
        serviceWithMock.makeSystemPrompt()

        val fakeBedrockResponse = ConverseResponse {
            output = ConverseOutput.Message(Message {
                role = ConversationRole.Assistant
                content = listOf(ContentBlock.Text("""
                    ```json
                    {
                      "intent": "add_list",
                      "data": "{\"listName\":\"Doms\"}",
                      "chat": ""
                    }
                    ```
                """.trimIndent()))
            })
            stopReason = StopReason.EndTurn
        }

        org.mockito.kotlin.whenever(mockBedrock.converse(org.mockito.kotlin.any())).thenReturn(fakeBedrockResponse)

        val result = serviceWithMock.resolve("c-789", "Add me to Doms please")
        assertNotNull(result.intent)
        assertTrue(result.intent is AddToListIntent)
        assertEquals("Doms", (result.intent as AddToListIntent).addToListData.listName)
    }
}
