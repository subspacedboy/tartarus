package club.subjugated.overlord_exe.bots.superbot

import club.subjugated.overlord_exe.OverlordExeApplication
import club.subjugated.overlord_exe.components.BSkyDmMonitor
import club.subjugated.overlord_exe.integration.config.IntegrationTestConfig
import club.subjugated.overlord_exe.integration.helpers.FakeBlueskyService
import club.subjugated.overlord_exe.models.BSkyLikesObjectiveState
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.EngagementPhase
import club.subjugated.overlord_exe.models.ObjectiveStatus
import club.subjugated.overlord_exe.models.SuperBotEngagementData
import club.subjugated.overlord_exe.objectives.BSkyLikesForm
import club.subjugated.overlord_exe.objectives.BSkyLikesObjective
import club.subjugated.overlord_exe.objectives.ObjectiveService
import club.subjugated.overlord_exe.services.BSkyUserService
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.web.EngagementWebController
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
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
    @Autowired lateinit var bSkyLikesObjective: BSkyLikesObjective
    @Autowired lateinit var objectiveService: ObjectiveService
    @Autowired lateinit var bSkyUserService: BSkyUserService
    @Autowired lateinit var blueSkyService: BlueSkyService
    @Autowired lateinit var engagementWebController: EngagementWebController
    @Autowired lateinit var superBot: SuperBot
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
            on { text } doReturn "! superbot"
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

        mockMvc.perform(get("/superbot/$token"))
            .andExpect(status().isOk)
            .andReturn()

        val intakeForm = IntakeForm(
            name = token,
            shareableToken = "sb-something",
            objectives = listOf("bsky_likes"),
        )

        mockMvc.perform(
            post("/superbot/submit")
                .param("name", intakeForm.name)
                .param("shareableToken", intakeForm.shareableToken)
                .param("objectives", intakeForm.objectives.joinToString(","))
        )
            .andExpect(status().isOk)

        val engagementAfterSubmit = superBot.findByName(token)
        val serialNumber = engagementAfterSubmit.contractSerialNumber

        var contract = Contract(
            botName = "superbot",
            lockSessionToken = "TOKEN",
            serialNumber = serialNumber,
        )
        contract = contractService.save(contract)

        superBot.handleAccept(contract)

        // This will trigger an info request DM
        val message2 = fakeBlueSkyService.sentMessages.last()
        val regex2 = Regex("""ir-[A-Za-z0-9]{7}""")
        val token2 = regex2.find(message2.second)?.value!!

        mockMvc.perform(get("/info/$token2"))
            .andExpect(status().isOk)
            .andReturn()

        val bskyLikesForm = BSkyLikesForm(
            name = token2,
            did = "did:plc:abc123",
            goal = 42,
        )
        mockMvc.perform(
            post("/info/submit").withBSkyLikesForm(bskyLikesForm)
        ).andExpect(status().isOk)

        // Set goal to 0 so the next review tick completes the objective
        val engagementInProgress = superBot.findByName(token)
        val superBotData = engagementInProgress.data as SuperBotEngagementData
        val likesState = superBotData.activeObjective as BSkyLikesObjectiveState
        likesState.goal = 0
        superBot.save(engagementInProgress)

        superBot.reviewContracts(listOf(contract))

        superBot.handleRelease(contract)

        // Verify final state
        val finalEngagement = superBot.findByName(token)
        val finalData = finalEngagement.data as SuperBotEngagementData
        val finalObjective = finalData.activeObjective as BSkyLikesObjectiveState
        assertThat(finalObjective.status).isEqualTo(ObjectiveStatus.COMPLETE)
        assertThat(finalEngagement.phase).isEqualTo(EngagementPhase.COMPLETE)
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
