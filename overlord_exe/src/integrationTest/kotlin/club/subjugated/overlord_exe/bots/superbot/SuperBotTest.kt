package club.subjugated.overlord_exe.bots.superbot

import club.subjugated.overlord_exe.OverlordExeApplication
import club.subjugated.overlord_exe.bots.superbot.web.IntakeForm
import club.subjugated.overlord_exe.components.BSkyDmMonitor
import club.subjugated.overlord_exe.integration.config.IntegrationTestConfig
import club.subjugated.overlord_exe.integration.helpers.FakeBlueskyService
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.StateMachineState
import club.subjugated.overlord_exe.services.BSkyUserService
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.services.StateMachineService
import club.subjugated.overlord_exe.statemachines.bsky_likes.BSkyLikesForm
import club.subjugated.overlord_exe.statemachines.bsky_likes.BSkyLikesStateMachine
import club.subjugated.overlord_exe.statemachines.bsky_likes.BSkyLikesStateMachineContext
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.web.InfoRequestWebController
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.Commit
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.annotation.Transactional
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import org.mockito.kotlin.whenever
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.ui.ExtendedModelMap
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageViewSender

@SpringBootTest(classes = [OverlordExeApplication::class, IntegrationTestConfig::class])
@TestPropertySource(locations = ["classpath:application-test.properties"])
@ActiveProfiles("test")
@Transactional
@AutoConfigureMockMvc
@Commit
class SuperBotTest {
    @Autowired lateinit var bSkyLikesStateMachine: BSkyLikesStateMachine
    @Autowired lateinit var stateMachineService: StateMachineService
    @Autowired lateinit var bSkyUserService: BSkyUserService
    @Autowired lateinit var blueSkyService: BlueSkyService
    @Autowired lateinit var infoRequestWebController: InfoRequestWebController
    @Autowired lateinit var superBotService: SuperBotService
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
    fun testSuperBotWithBSkyLikes() {
        val fakeBlueSkyService = blueSkyService as FakeBlueskyService

        val fakeSender = mock<ConvoDefsMessageViewSender> {
            on { did } doReturn "did:plc:qqk2pgu2uyqnbdp5jldvb3f2"
        }

        val fakeMsg = mock<ConvoDefsMessageView> {
            on { text } doReturn "own_me"
            on { sender } doReturn fakeSender
            on { sentAt } doReturn timeSource.nowInUtc().toString()
        }
        
        fakeBlueSkyService.fakeDms += "convo1" to fakeMsg

        chatDmMonitor.checkDms()

        // Get the magic link
        assertThat(fakeBlueSkyService.sentMessages.size).isEqualTo(1)

        val message = fakeBlueSkyService.sentMessages.first()
        val regex = Regex("""sb-[A-Za-z0-9]{7}""")
        val token = regex.find(message.second)?.value!!

        val result = mockMvc.perform(get("/superbot/$token"))
            .andExpect(status().isOk)
            .andReturn()

        val intakeForm = IntakeForm(
            name = token,
            shareableToken = "sb-something",
            objectives = listOf("bsky_likes")
        )

        mockMvc.perform(
            post("/superbot/submit")
                .param("name", intakeForm.name)
                .param("shareableToken", intakeForm.shareableToken)
                .param("objectives", intakeForm.objectives.joinToString(","))
        )
            .andExpect(status().isOk)

        val serialNumber = superBotService.findByName(token).contractSerialNumber

        var contract = Contract(
            botName = "superbot",
            lockSessionToken = "TOKEN",
            serialNumber = serialNumber
        )
        contract = contractService.save(contract)

        superBotService.handleAccept(contract)

        // This will trigger an info request

        val message2 = fakeBlueSkyService.sentMessages.last()
        val regex2 = Regex("""ir-[A-Za-z0-9]{7}""")
        val token2 = regex2.find(message2.second)?.value

        val model = ExtendedModelMap()

        val resultIr = mockMvc.perform(get("/info/$token2"))
            .andExpect(status().isOk)
            .andReturn()

        val bskyLikesForm = BSkyLikesForm(
            name = token2!!,
            did = "did:plc:abc123",
            goal = 42
        )
        mockMvc.perform(
            post("/info/submit").withBSkyLikesForm(bskyLikesForm)
        ).andExpect(status().isOk)

        var sm = stateMachineService.findByOwnedBy(contract.name)
        val ctx = sm.first().context as BSkyLikesStateMachineContext
        ctx.goal = 0
        stateMachineService.save(sm.first())

        superBotService.reviewContracts(listOf(contract))

        superBotService.handleRelease(contract)

        // All the asserts
        val stateMachine = stateMachineService.findByOwnedBy(contract.name).first()
        assertThat(stateMachine.state).isEqualTo(StateMachineState.COMPLETE)
        assertThat(stateMachine.currentState).isEqualTo("COMPLETE")
        assertThat(superBotService.findByName(token).state).isEqualTo(SuperBotRecordState.COMPLETE)

        println("DONE")
    }
}

fun MockHttpServletRequestBuilder.withIntakeForm(form: IntakeForm): MockHttpServletRequestBuilder =
    this.param("name", form.name)
        .param("shareableToken", form.shareableToken)
        .apply { form.objectives.forEach { param("objectives", it) } }

fun MockHttpServletRequestBuilder.withBSkyLikesForm(form: BSkyLikesForm): MockHttpServletRequestBuilder =
    this.param("name", form.name)
        .param("did", form.did)
        .param("goal", form.goal.toString())
