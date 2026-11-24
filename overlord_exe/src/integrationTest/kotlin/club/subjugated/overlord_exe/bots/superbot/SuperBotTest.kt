package club.subjugated.overlord_exe.bots.superbot

import club.subjugated.overlord_exe.OverlordExeApplication
import club.subjugated.overlord_exe.bots.overlord.OverlordControlBot
import club.subjugated.overlord_exe.components.BSkyDmMonitor
import club.subjugated.overlord_exe.integration.config.IntegrationTestConfig
import club.subjugated.overlord_exe.integration.helpers.FakeBlueskyService
import club.subjugated.overlord_exe.machines.SessionEngine
import club.subjugated.overlord_exe.models.BSkyLikesMachineState
import club.subjugated.overlord_exe.models.MachineStatus
import club.subjugated.overlord_exe.models.SessionPhase
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.util.TimeSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.Commit
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageViewSender

@SpringBootTest(classes = [OverlordExeApplication::class, IntegrationTestConfig::class])
@TestPropertySource(locations = ["classpath:application-test.properties"])
@ActiveProfiles("test")
@Transactional
@AutoConfigureMockMvc
@Commit
class SuperBotTest {
    @Autowired lateinit var blueSkyService: BlueSkyService
    @Autowired lateinit var overlordControlBot: OverlordControlBot
    @Autowired lateinit var sessionEngine: SessionEngine
    @Autowired lateinit var contractService: ContractService
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var timeSource: TimeSource
    @Autowired lateinit var chatDmMonitor: BSkyDmMonitor

    @BeforeEach
    fun resetFakes() {
        val fakeBlueSkyService = blueSkyService as FakeBlueskyService
        fakeBlueSkyService.reset()
    }

    @Test
    fun testUnifiedSessionWithBSkyLikes() {
        val fakeBlueSkyService = blueSkyService as FakeBlueskyService
        val fakeSender = mock<ConvoDefsMessageViewSender> {
            on { did } doReturn "did:plc:qqk2pgu2uyqnbdp5jldvb3f2"
        }
        val fakeMsg = mock<ConvoDefsMessageView> {
            on { text } doReturn "!superbot"
            on { sender } doReturn fakeSender
            on { sentAt } doReturn timeSource.nowInUtc().toString()
        }

        fakeBlueSkyService.fakeDms += "convo1" to fakeMsg
        chatDmMonitor.checkDms()

        assertThat(fakeBlueSkyService.sentMessages.size).isEqualTo(1)
        val token = Regex("""ses-[A-Za-z0-9]{7}""").find(fakeBlueSkyService.sentMessages.first().second)?.value!!

        mockMvc.perform(get("/session/$token"))
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/session/submit")
                .param("token", token)
                .param("shareableToken", "s-TEST123")
                .param("machines", "bsky_likes")
        ).andExpect(status().isOk)

        val issued = sessionEngine.findByToken(token)
        assertThat(issued.phase).isEqualTo(SessionPhase.ISSUED)

        val contract = contractService.findById(issued.contractId!!)!!
        overlordControlBot.handleAccept(contract)

        val active = sessionEngine.findByToken(token)
        assertThat(active.phase).isEqualTo(SessionPhase.ACTIVE)

        val machine = sessionEngine.findMachines(active.id).first()
        assertThat(machine.status).isEqualTo(MachineStatus.WAITING_FORM)
        assertThat(machine.state).isInstanceOf(BSkyLikesMachineState::class.java)
    }
}
