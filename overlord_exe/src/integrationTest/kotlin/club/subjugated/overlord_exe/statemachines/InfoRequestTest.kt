package club.subjugated.overlord_exe.statemachines

import club.subjugated.overlord_exe.OverlordExeApplication
import club.subjugated.overlord_exe.integration.config.IntegrationTestConfig
import club.subjugated.overlord_exe.integration.helpers.FakeBlueskyService
import club.subjugated.overlord_exe.models.BSkyLikesObjectiveState
import club.subjugated.overlord_exe.models.BotEngagement
import club.subjugated.overlord_exe.models.EngagementPhase
import club.subjugated.overlord_exe.models.ObjectiveStatus
import club.subjugated.overlord_exe.models.SuperBotEngagementData
import club.subjugated.overlord_exe.objectives.ObjectiveService
import club.subjugated.overlord_exe.services.BSkyUserService
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.storage.BotEngagementRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
import kotlin.test.Test

@SpringBootTest(classes = [OverlordExeApplication::class, IntegrationTestConfig::class])
@TestPropertySource(locations = ["classpath:application-test.properties"])
@ActiveProfiles("test")
@Transactional
@AutoConfigureMockMvc
@Commit
class InfoRequestTest {
    @Autowired lateinit var objectiveService: ObjectiveService
    @Autowired lateinit var engagementRepository: BotEngagementRepository
    @Autowired lateinit var bSkyUserService: BSkyUserService
    @Autowired lateinit var blueSkyService: BlueSkyService

    @Autowired lateinit var mockMvc: MockMvc

    @BeforeEach
    fun resetFakes() {
        val fakeBlueSkyService = blueSkyService as FakeBlueskyService
        fakeBlueSkyService.reset()
    }

    @Test
    fun testInfoRequest() {
        val user = bSkyUserService.findOrCreateByDid("did:plc:qqk2pgu2uyqnbdp5jldvb3f2")
        user.convoId = "3lpwoh5skzn2q"
        bSkyUserService.save(user)

        val engagement = BotEngagement(
            botId = "superBot",
            phase = EngagementPhase.ACTIVE,
            subjectDid = user.did,
            convoId = user.convoId!!,
            data = SuperBotEngagementData(objectives = listOf("bsky_likes")),
        )
        engagementRepository.save(engagement)

        val state = objectiveService.startObjective(engagement, "bsky_likes")
        assertThat(state.status).isEqualTo(ObjectiveStatus.WAITING)

        val fake = blueSkyService as FakeBlueskyService
        assertThat(fake.sentMessages.size).isEqualTo(1)

        val message = fake.sentMessages.first()
        val regex = Regex("""ir-[A-Za-z0-9]{7}""")
        val token = regex.find(message.second)?.value!!

        mockMvc.perform(get("/info/$token"))
            .andExpect(status().isOk)
            .andReturn()

        mockMvc.perform(
            post("/info/submit")
                .param("name", token)
                .param("did", "did:plc:abc123")
                .param("goal", "42")
        )
            .andExpect(status().isOk)

        val updated = engagementRepository.findByName(engagement.name)!!
        val data = updated.data as SuperBotEngagementData
        val likesState = data.activeObjective as BSkyLikesObjectiveState
        assertThat(likesState.goal).isEqualTo(42)
        assertThat(likesState.status).isEqualTo(ObjectiveStatus.IN_PROGRESS)
        // Should _NOT_ have replaced subject's DID with form's did parameter
        assertThat(likesState.did).isEqualTo("did:plc:qqk2pgu2uyqnbdp5jldvb3f2")
    }
}
