package club.subjugated.overlord_exe.statemachines

import club.subjugated.overlord_exe.OverlordExeApplication
import club.subjugated.overlord_exe.integration.config.IntegrationTestConfig
import club.subjugated.overlord_exe.integration.helpers.FakeBlueskyService
import club.subjugated.overlord_exe.services.BSkyUserService
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.StateMachineService
import club.subjugated.overlord_exe.statemachines.bsky_likes.BSkyLikesForm
import club.subjugated.overlord_exe.statemachines.bsky_likes.BSkyLikesStateMachine
import club.subjugated.overlord_exe.statemachines.bsky_likes.BSkyLikesStateMachineContext
import club.subjugated.overlord_exe.web.InfoRequestWebController
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.Commit
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.transaction.annotation.Transactional
import org.springframework.ui.ExtendedModelMap
import org.springframework.ui.Model
import kotlin.test.Test

@SpringBootTest(classes = [OverlordExeApplication::class, IntegrationTestConfig::class])
@TestPropertySource(locations = ["classpath:application-test.properties"])
@Transactional
@AutoConfigureMockMvc
@Commit
class InfoRequestTest {
    @Autowired lateinit var bSkyLikesStateMachine: BSkyLikesStateMachine
    @Autowired lateinit var stateMachineService: StateMachineService
    @Autowired lateinit var bSkyUserService: BSkyUserService
    @Autowired lateinit var blueSkyService: BlueSkyService
    @Autowired lateinit var infoRequestWebController: InfoRequestWebController

    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun testInfoRequest() {
        val user = bSkyUserService.findOrCreateByDid("did:plc:qqk2pgu2uyqnbdp5jldvb3f2")
        user.convoId = "3lpwoh5skzn2q"
        bSkyUserService.save(user)

        val form = BSkyLikesForm(
            did = "did:plc:qqk2pgu2uyqnbdp5jldvb3f2",
            goal = 100
        )

        val sm = stateMachineService.createNewStateMachine(
            ownerName = "c-blahblah",
            providerClassName = BSkyLikesStateMachine::class.qualifiedName!!,
            form = form,
            bskyUser = user
        )

        stateMachineService.process(sm)

        assertThat(sm.currentState).isEqualTo("WAITING")

        val fake = blueSkyService as FakeBlueskyService
        assertThat(fake.sentMessages.size).isEqualTo(1)

        val message = fake.sentMessages.first()
        val regex = Regex("""ir-[A-Za-z0-9]{7}""")
        val token = regex.find(message.second)?.value

        val model = ExtendedModelMap()

        val result = mockMvc.perform(get("/info/$token"))
            .andExpect(status().isOk)
            .andReturn()

        val renderedHtml = result.response.contentAsString

        mockMvc.perform(
            post("/info/submit")
                .param("name", token)
                .param("did", "did:plc:abc123")
                .param("goal", "42")
//                .with(csrf()) // include if CSRF protection is enabled
        )
            .andExpect(status().isOk)

        val sm2 = stateMachineService.findByName(sm.name)
        val ctx = sm2.context as BSkyLikesStateMachineContext
        assertThat(ctx.goal).isEqualTo(42)
        assertThat(sm2.currentState).isEqualTo("IN_PROGRESS")
        // Should _NOT_ have replaced.
        assertThat(ctx.did).isEqualTo("did:plc:qqk2pgu2uyqnbdp5jldvb3f2")
    }
}