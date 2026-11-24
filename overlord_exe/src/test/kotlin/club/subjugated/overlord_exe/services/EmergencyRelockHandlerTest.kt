package club.subjugated.overlord_exe.services

import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.bots.overlord.OverlordControlBot
import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractSession
import club.subjugated.overlord_exe.models.ContractState
import club.subjugated.overlord_exe.models.SessionPhase
import club.subjugated.overlord_exe.models.UserContext
import club.subjugated.overlord_exe.models.UserProfile
import club.subjugated.overlord_exe.models.UserShareableToken
import club.subjugated.overlord_exe.runtime.BotContext
import club.subjugated.overlord_exe.storage.ContractSessionRepository
import club.subjugated.overlord_exe.util.ShareableTokenType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageViewSender

class EmergencyRelockHandlerTest {

    private lateinit var contractService: ContractService
    private lateinit var sessionRepository: ContractSessionRepository
    private lateinit var botComponent: BotComponent
    private lateinit var botContext: BotContext
    private lateinit var emergencyRelockService: EmergencyRelockService

    @BeforeEach
    fun setUp() {
        contractService = mock()
        sessionRepository = mock()
        botComponent = mock()
        botContext = mock()
        emergencyRelockService = EmergencyRelockService(
            contractService = contractService,
            sessionRepository = sessionRepository,
            botComponent = botComponent,
        )
    }

    @Test
    fun `relockForUser syncs and locks emergency-unlocked contract via session`() {
        val contract = Contract(
            id = 10L,
            name = "bc-10",
            externalContractName = "c-ext-10",
            botName = "b-overlord",
            lockSessionToken = "ls-10",
            shareableToken = "s-TOK10",
            serialNumber = 1,
            state = ContractState.ACCEPTED,
            emergencyUnlocked = true,
        )
        val session = ContractSession(
            id = 1L,
            token = "ses-1",
            botId = "overlord",
            phase = SessionPhase.ACTIVE,
            contractId = 10L,
            contractSerialNumber = 1,
            shareableToken = "s-TOK10",
            subjectDid = "did:plc:sub1",
            convoId = "c-1",
        )

        whenever(contractService.findContractsForShareableToken("s-TOK10")).thenReturn(listOf(contract))
        whenever(sessionRepository.findBySubjectDidAndPhaseIn(any(), any())).thenReturn(listOf(session))
        whenever(sessionRepository.findByConvoIdAndPhaseIn(any(), any())).thenReturn(listOf(session))
        whenever(contractService.findById(10L)).thenReturn(contract)
        whenever(botComponent.contextForExternalName("b-overlord")).thenReturn(botContext)
        whenever(botContext.syncContract("ls-10", 1)).thenReturn(contract)
        whenever(botContext.lock(contract)).thenReturn(contract)

        val userCtx = UserContext(
            profile = UserProfile(did = "did:plc:sub1", convoId = "c-1"),
            knownTokens = listOf(UserShareableToken(userDid = "did:plc:sub1", token = "s-TOK10", tokenType = ShareableTokenType.S)),
            activeAsSubject = emptyList(),
            activeAsKeyholder = emptyList(),
            completedSessionCount = 0,
        )
        val sender = mock<ConvoDefsMessageViewSender> { on { did } doReturn "did:plc:sub1" }
        val message = mock<ConvoDefsMessageView> { on { this.sender } doReturn sender }
        val convoCtx = ConversationContext(userContext = userCtx, convoId = "c-1", currentMessage = message)

        val reply = emergencyRelockService.relockForUser(convoCtx)

        assertTrue(reply.contains("Sent relock command for 1 contract"))
        verify(botContext).lock(contract)
    }

    @Test
    fun `OverlordControlBot routes !relock command to EmergencyRelockService`() {
        val bot = OverlordControlBot(
            botMapService = mock(),
            botComponent = botComponent,
            sessionEngine = mock(),
            userProfileService = mock(),
            contractService = contractService,
            blueSkyService = mock(),
            emergencyRelockService = emergencyRelockService,
        )
        whenever(sessionRepository.findBySubjectDidAndPhaseIn(any(), any())).thenReturn(emptyList())
        whenever(sessionRepository.findByConvoIdAndPhaseIn(any(), any())).thenReturn(emptyList())

        val sender = mock<ConvoDefsMessageViewSender> { on { did } doReturn "did:plc:sub1" }
        val message = mock<ConvoDefsMessageView> { on { this.sender } doReturn sender }
        val convoCtx = ConversationContext(
            userContext = UserContext(
                profile = UserProfile(did = "did:plc:sub1"),
                knownTokens = emptyList(),
                activeAsSubject = emptyList(),
                activeAsKeyholder = emptyList(),
                completedSessionCount = 0,
            ),
            convoId = "c-1",
            currentMessage = message,
        )

        assertEquals("No active contracts found for your account.", bot.handleCommand(convoCtx, "relock", emptyList()))
    }
}
