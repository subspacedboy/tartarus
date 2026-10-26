package club.subjugated.overlord_exe.services

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.ContentBlock
import aws.sdk.kotlin.services.bedrockruntime.model.ConversationRole
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseOutput
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseResponse
import aws.sdk.kotlin.services.bedrockruntime.model.Message
import aws.sdk.kotlin.services.bedrockruntime.model.StopReason
import aws.sdk.kotlin.services.bedrockruntime.model.ToolUseBlock
import aws.smithy.kotlin.runtime.content.Document
import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.convo.ConversationHandler
import club.subjugated.overlord_exe.convo.ConversationResponse
import club.subjugated.overlord_exe.convo.IntentSpec
import club.subjugated.overlord_exe.convo.stringIntent
import club.subjugated.overlord_exe.convo.unitIntent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
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
        ) { _, listName ->
            ConversationResponse("Added to $listName")
        },
        stringIntent(
            name = "remove_list",
            paramName = "listName",
            paramDescription = "Name of the Bluesky list to leave ('Doms', 'Subs', or 'Public Property')",
            description = "The user wants to be removed from a Bluesky list.",
        ) { _, listName ->
            ConversationResponse("Removed from $listName")
        },
    )
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
            globalRateLimitWindowSeconds = 60,
        )

        intentService = IntentService(
            intentProviders = listOf(TestConversationHandler()),
            rateLimiter = rateLimiter,
        )
    }

    @Test
    fun `buildToolConfiguration registers all declarative IntentSpecs with JSON Schema`() {
        val toolConfig = intentService.buildToolConfiguration()
        assertNotNull(toolConfig)
        val specs = toolConfig!!.tools.map { it.asToolSpec() }
        assertEquals(listOf("hello", "add_list", "remove_list"), specs.map { it.name })

        val addListSpec = specs.first { it.name == "add_list" }
        val schemaMap = addListSpec.inputSchema!!.asJson().asMap()
        assertEquals("object", schemaMap["type"]?.asString())
        val required = schemaMap["required"]?.asList()?.mapNotNull { it?.asString() }
        assertEquals(listOf("listName"), required)
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
            intentService.addMessageToContext(
                convoId,
                Message {
                    role = ConversationRole.User
                    content = listOf(ContentBlock.Text("User turn $i"))
                }
            )
            intentService.addMessageToContext(
                convoId,
                Message {
                    role = ConversationRole.Assistant
                    content = listOf(ContentBlock.Text("Assistant turn $i"))
                }
            )
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
            intentService.addMessageToContext(
                convoId,
                Message {
                    role = ConversationRole.User
                    content = listOf(ContentBlock.Text("U$i"))
                }
            )
            intentService.addMessageToContext(
                convoId,
                Message {
                    role = ConversationRole.Assistant
                    content = listOf(ContentBlock.Text("A$i"))
                }
            )
        }

        assertEquals(6, intentService.getContext(convoId).size)

        val trimmed = intentService.getTrimmedContext(convoId)
        assertEquals(4, trimmed.size)
        assertEquals(ConversationRole.User, trimmed.first().role)
        assertEquals("U2", trimmed.first().content.first().asText())

        intentService.maxContextMessages = 3
        val trimmedOdd = intentService.getTrimmedContext(convoId)
        assertEquals(2, trimmedOdd.size)
        assertEquals(ConversationRole.User, trimmedOdd.first().role)
        assertEquals("U3", trimmedOdd.first().content.first().asText())
    }

    @Test
    fun `reifyToolUse reconstructs strongly typed IntentCalls and executes handler`() {
        val addListCall = intentService.reifyToolUse(
            "add_list",
            Document(mapOf("listName" to Document("Doms"))),
        )
        assertEquals("add_list", addListCall.name)
        assertEquals("Doms", addListCall.payload)

        val dummyCtx = ConversationContext(
            bskyUser = null,
            convoId = "c-1",
            currentMessage = mock<ConvoDefsMessageView>(),
        )
        assertEquals("Added to Doms", intentService.dispatch(dummyCtx, addListCall).text)

        val helloCall = intentService.reifyToolUse("hello", Document(emptyMap()))
        assertEquals("hello", helloCall.name)
        assertEquals(Unit, helloCall.payload)
    }

    @Test
    fun `resolve handles native Bedrock ToolUseBlock and clears context`() = runBlocking {
        val mockBedrock = mock<BedrockRuntimeClient>()
        val serviceWithMock = IntentService(
            intentProviders = listOf(TestConversationHandler()),
            rateLimiter = rateLimiter,
            injectedBedrockClient = mockBedrock,
        )

        val fakeBedrockResponse = ConverseResponse {
            output = ConverseOutput.Message(
                Message {
                    role = ConversationRole.Assistant
                    content = listOf(
                        ContentBlock.ToolUse(
                            ToolUseBlock {
                                toolUseId = "tu-1"
                                name = "hello"
                                input = Document(emptyMap())
                            }
                        )
                    )
                }
            )
            stopReason = StopReason.ToolUse
        }

        org.mockito.kotlin.whenever(mockBedrock.converse(org.mockito.kotlin.any())).thenReturn(fakeBedrockResponse)

        val result = serviceWithMock.resolve("c-123", "Hello bot")
        assertNotNull(result.intent)
        assertEquals("hello", result.intent!!.name)
        assertEquals("", result.chat)
        assertEquals(0, serviceWithMock.getContext("c-123").size)
    }

    @Test
    fun `resolve handles Bedrock follow up text chat and retains context`() = runBlocking {
        val mockBedrock = mock<BedrockRuntimeClient>()
        val serviceWithMock = IntentService(
            intentProviders = listOf(TestConversationHandler()),
            rateLimiter = rateLimiter,
            injectedBedrockClient = mockBedrock,
        )

        val fakeBedrockResponse = ConverseResponse {
            output = ConverseOutput.Message(
                Message {
                    role = ConversationRole.Assistant
                    content = listOf(ContentBlock.Text("Which list would you like to join?"))
                }
            )
            stopReason = StopReason.EndTurn
        }

        org.mockito.kotlin.whenever(mockBedrock.converse(org.mockito.kotlin.any())).thenReturn(fakeBedrockResponse)

        val result = serviceWithMock.resolve("c-456", "I want to join a list")
        assertNull(result.intent)
        assertEquals("Which list would you like to join?", result.chat)
        assertEquals(2, serviceWithMock.getContext("c-456").size)
    }

    @Test
    fun `resolve asks user for clarification when tool call omits required parameter`() = runBlocking {
        val mockBedrock = mock<BedrockRuntimeClient>()
        val serviceWithMock = IntentService(
            intentProviders = listOf(TestConversationHandler()),
            rateLimiter = rateLimiter,
            injectedBedrockClient = mockBedrock,
        )

        val fakeBedrockResponse = ConverseResponse {
            output = ConverseOutput.Message(
                Message {
                    role = ConversationRole.Assistant
                    content = listOf(
                        ContentBlock.ToolUse(
                            ToolUseBlock {
                                toolUseId = "tu-2"
                                name = "add_list"
                                input = Document(emptyMap()) // Missing required listName!
                            }
                        )
                    )
                }
            )
            stopReason = StopReason.ToolUse
        }

        org.mockito.kotlin.whenever(mockBedrock.converse(org.mockito.kotlin.any())).thenReturn(fakeBedrockResponse)

        val result = serviceWithMock.resolve("c-789", "Add me to a list")
        assertNull(result.intent)
        assertTrue(result.chat!!.contains("Name of the Bluesky list to join"))
        // Context retained so the user can answer in the next turn
        assertEquals(2, serviceWithMock.getContext("c-789").size)
    }

    @Test
    fun `resolve strips Nova thinking tags from assistant text and falls back if only thinking was emitted`() = runBlocking {
        val mockBedrock = mock<BedrockRuntimeClient>()
        val serviceWithMock = IntentService(
            intentProviders = listOf(TestConversationHandler()),
            rateLimiter = rateLimiter,
            injectedBedrockClient = mockBedrock,
        )

        // Case 1: <thinking> block followed by user-facing question
        val mixedResponse = ConverseResponse {
            output = ConverseOutput.Message(
                Message {
                    role = ConversationRole.Assistant
                    content = listOf(
                        ContentBlock.Text(
                            "<thinking>The user wants to join a list, but didn't say which one.</thinking>\nWhich list would you like to join?"
                        )
                    )
                }
            )
            stopReason = StopReason.EndTurn
        }
        org.mockito.kotlin.whenever(mockBedrock.converse(org.mockito.kotlin.any())).thenReturn(mixedResponse)

        val result1 = serviceWithMock.resolve("c-think-1", "Add me to a list")
        assertNull(result1.intent)
        assertEquals("Which list would you like to join?", result1.chat)
        assertTrue(!result1.chat!!.contains("<thinking>"))

        // Case 2: Only <thinking> block emitted by Nova Lite
        val thinkingOnlyResponse = ConverseResponse {
            output = ConverseOutput.Message(
                Message {
                    role = ConversationRole.Assistant
                    content = listOf(
                        ContentBlock.Text(
                            "<thinking>The user has asked for help but has not specified what kind of help they need. I need to ask for more information to determine which tool to invoke.</thinking>"
                        )
                    )
                }
            )
            stopReason = StopReason.EndTurn
        }
        org.mockito.kotlin.whenever(mockBedrock.converse(org.mockito.kotlin.any())).thenReturn(thinkingOnlyResponse)

        val result2 = serviceWithMock.resolve("c-think-2", "help")
        assertNull(result2.intent)
        assertTrue(!result2.chat!!.contains("<thinking>"))
        assertTrue(result2.chat!!.contains("What would you like me to help you with?"))
    }
}
