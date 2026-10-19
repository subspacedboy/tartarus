package club.subjugated.overlord_exe.components

import club.subjugated.overlord_exe.bots.simple_proxy.convo.SimpleProxyConvoHandler
import club.subjugated.overlord_exe.bots.superbot.convo.SuperBotConvoHandler
import club.subjugated.overlord_exe.bots.timer_bot.convo.TimerBotConvoHandler
import club.subjugated.overlord_exe.intents.OverlordConversationHandler
import club.subjugated.overlord_exe.models.BSkyUser
import club.subjugated.overlord_exe.services.BSkyUserService
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.IntentService
import club.subjugated.overlord_exe.services.ResolvedIntent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.quartz.DisallowConcurrentExecution
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageViewSender

class BSkyDmMonitorTest {

    private val blueSkyService: BlueSkyService = mock()
    private val timerBotConversationHandler: TimerBotConvoHandler = mock()
    private val superBotConvoHandler: SuperBotConvoHandler = mock()
    private val bSkyUserService: BSkyUserService = mock()
    private val simpleProxyConvoHandler: SimpleProxyConvoHandler = mock()
    private val overlordConversationHandler: OverlordConversationHandler = mock()
    private val intentService: IntentService = mock()

    private lateinit var monitor: BSkyDmMonitor

    @BeforeEach
    fun setUp() {
        monitor = BSkyDmMonitor(
            blueSkyService = blueSkyService,
            timerBotConversationHandler = timerBotConversationHandler,
            superBotConvoHandler = superBotConvoHandler,
            bSkyUserService = bSkyUserService,
            simpleProxyConvoHandler = simpleProxyConvoHandler,
            overlordConversationHandler = overlordConversationHandler,
            intentService = intentService,
        )

        val user = BSkyUser(did = "did:plc:test1234", handle = "test.bsky.social")
        whenever(bSkyUserService.findOrCreateByDid(any())).thenReturn(user)
        whenever(blueSkyService.resolveDidToHandle(any())).thenReturn("test.bsky.social")
    }

    @Test
    fun `class has DisallowConcurrentExecution annotation`() {
        val annotation = BSkyDmMonitor::class.java.getAnnotation(DisallowConcurrentExecution::class.java)
        assertEquals(true, annotation != null, "BSkyDmMonitor should be annotated with @DisallowConcurrentExecution")
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

        // First execution processes the message
        monitor.handleDm("convo-1", message)
        verify(blueSkyService, times(1)).sendDm("convo-1", "Hello 😈")

        // Second execution with same message ID is skipped
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

        whenever(intentService.resolve("convo-1", "What can you do?")).thenReturn(
            ResolvedIntent(chat = "I can lock and unlock devices for you.", intent = null)
        )

        monitor.handleDm("convo-1", message)
        verify(blueSkyService, times(1)).sendDm("convo-1", "I can lock and unlock devices for you.")
    }
}
