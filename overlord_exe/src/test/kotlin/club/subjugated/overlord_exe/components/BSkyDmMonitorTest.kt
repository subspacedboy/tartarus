package club.subjugated.overlord_exe.components

import club.subjugated.overlord_exe.bots.overlord.OverlordControlBot
import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.convo.ConversationHandler
import club.subjugated.overlord_exe.models.UserContext
import club.subjugated.overlord_exe.models.UserProfile
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.IntentService
import club.subjugated.overlord_exe.services.ResolvedIntent
import club.subjugated.overlord_exe.services.UserContextService
import club.subjugated.overlord_exe.services.UserProfileService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageViewSender

class BSkyDmMonitorTest {

    private val blueSkyService: BlueSkyService = mock()
    private val userProfileService: UserProfileService = mock()
    private val userContextService: UserContextService = mock()
    private val overlordControlBot: OverlordControlBot = mock()
    private val intentService: IntentService = mock()

    private lateinit var monitor: BSkyDmMonitor
    private lateinit var userContext: UserContext

    @BeforeEach
    fun setUp() {
        monitor = BSkyDmMonitor(
            blueSkyService = blueSkyService,
            userProfileService = userProfileService,
            userContextService = userContextService,
            intentService = intentService,
            conversationHandlers = listOf(overlordControlBot),
        )

        val profile = UserProfile(did = "did:plc:test1234", handle = "test.bsky.social")
        userContext = UserContext(
            profile = profile,
            knownTokens = emptyList(),
            activeAsSubject = emptyList(),
            activeAsKeyholder = emptyList(),
            completedSessionCount = 0,
        )
        whenever(userProfileService.learnFromDm(any(), any(), anyOrNull(), any())).thenReturn(profile)
        whenever(userContextService.buildContextForDid(any())).thenReturn(userContext)
        whenever(blueSkyService.resolveDidToHandle(any())).thenReturn("test.bsky.social")
    }

    @Test
    fun `deduplicates identical message ids`() {
        val sender = mock<ConvoDefsMessageViewSender> {
            on { did } doReturn "did:plc:test1234"
        }
        val message = mock<ConvoDefsMessageView> {
            on { id } doReturn "msg-unique-123"
            on { text } doReturn "!hello"
            on { this.sender } doReturn sender
        }

        monitor.handleDm("convo-1", message)
        verify(blueSkyService, times(1)).sendDm("convo-1", "Hello 😈")

        monitor.handleDm("convo-1", message)
        verify(blueSkyService, times(1)).sendDm(any(), any())
    }

    @Test
    fun `does not send blank responses`() {
        val sender = mock<ConvoDefsMessageViewSender> {
            on { did } doReturn "did:plc:test1234"
        }
        val message = mock<ConvoDefsMessageView> {
            on { id } doReturn "msg-unknown-cmd"
            on { text } doReturn "!unknowncommand"
            on { this.sender } doReturn sender
        }

        monitor.handleDm("convo-1", message)
        verify(blueSkyService, never()).sendDm(eq("convo-1"), any())
    }

    @Test
    fun `processes conversational messages with intentService and sends response`() {
        val sender = mock<ConvoDefsMessageViewSender> {
            on { did } doReturn "did:plc:test1234"
        }
        val message = mock<ConvoDefsMessageView> {
            on { id } doReturn "msg-conv-1"
            on { text } doReturn "What can you do?"
            on { this.sender } doReturn sender
        }

        whenever(intentService.resolve("convo-1", "What can you do?", userContext)).thenReturn(
            ResolvedIntent(chat = "I can lock and unlock devices for you.", intent = null)
        )

        monitor.handleDm("convo-1", message)
        verify(blueSkyService, times(1)).sendDm("convo-1", "I can lock and unlock devices for you.")
    }

    @Test
    fun `dynamically routes bang commands to ConversationHandlers declaring matching commands`() {
        val customHandler = object : ConversationHandler {
            override val commands: Set<String> = setOf("custombot")
            override fun handleCommand(
                ctx: ConversationContext,
                command: String,
                args: List<String>,
            ): String = "http://localhost:8090/custom/${args.firstOrNull()}"
        }

        val dynamicMonitor = BSkyDmMonitor(
            blueSkyService = blueSkyService,
            userProfileService = userProfileService,
            userContextService = userContextService,
            intentService = intentService,
            conversationHandlers = listOf(customHandler),
        )

        val sender = mock<ConvoDefsMessageViewSender> {
            on { did } doReturn "did:plc:test1234"
        }
        val message = mock<ConvoDefsMessageView> {
            on { id } doReturn "msg-custom-cmd"
            on { text } doReturn "!custombot tok-1"
            on { this.sender } doReturn sender
        }

        dynamicMonitor.handleDm("convo-1", message)
        verify(blueSkyService, times(1)).sendDm("convo-1", "http://localhost:8090/custom/tok-1")
    }
}
