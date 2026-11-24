package club.subjugated.overlord_exe.statemachines

import club.subjugated.overlord_exe.OverlordExeApplication
import club.subjugated.overlord_exe.bots.overlord.OverlordControlBot
import club.subjugated.overlord_exe.integration.config.IntegrationTestConfig
import club.subjugated.overlord_exe.integration.helpers.FakeBlueskyService
import club.subjugated.overlord_exe.machines.SessionEngine
import club.subjugated.overlord_exe.machines.SessionIssueSpec
import club.subjugated.overlord_exe.models.BSkyLikesMachineState
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractState
import club.subjugated.overlord_exe.models.MachineStatus
import club.subjugated.overlord_exe.models.ReleasePolicy
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.UserProfileService
import club.subjugated.overlord_exe.storage.SessionMachineRepository
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
    @Autowired lateinit var sessionEngine: SessionEngine
    @Autowired lateinit var machineRepository: SessionMachineRepository
    @Autowired lateinit var userProfileService: UserProfileService
    @Autowired lateinit var overlordControlBot: OverlordControlBot
    @Autowired lateinit var blueSkyService: BlueSkyService
    @Autowired lateinit var mockMvc: MockMvc

    @BeforeEach
    fun resetFakes() {
        val fakeBlueSkyService = blueSkyService as FakeBlueskyService
        fakeBlueSkyService.reset()
    }

    @Test
    fun testInfoRequest() {
        val user = userProfileService.findOrCreateByDid("did:plc:qqk2pgu2uyqnbdp5jldvb3f2")
        user.convoId = "3lpwoh5skzn2q"
        userProfileService.save(user)

        val session = sessionEngine.createDraftSession(
            subjectDid = user.did,
            convoId = user.convoId!!,
        )
        sessionEngine.issueSession(
            ctx = overlordControlBot.botContext,
            session = session,
            spec = SessionIssueSpec(
                shareableToken = "s-INFO123",
                isPublic = false,
                releasePolicy = ReleasePolicy.ALL,
                machineTypes = listOf("bsky_likes"),
            ),
        )

        val contract = Contract(
            id = session.contractId!!,
            botName = overlordControlBot.botContext.externalName,
            lockSessionToken = "ls-TEST",
            shareableToken = "s-INFO123",
            serialNumber = session.contractSerialNumber,
            state = ContractState.ACCEPTED,
        )
        sessionEngine.onAccept(overlordControlBot.botContext, contract)

        val fake = blueSkyService as FakeBlueskyService
        val infoMsg = fake.sentMessages.last { it.second.contains("ir-") }
        val token = Regex("""ir-[A-Za-z0-9]{7}""").find(infoMsg.second)?.value!!

        mockMvc.perform(get("/info/$token"))
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/info/submit")
                .param("name", token)
                .param("did", "did:plc:abc123")
                .param("goal", "42")
        )
            .andExpect(status().isOk)

        val machine = sessionEngine.findMachines(session.id).first()
        val likesState = machine.state as BSkyLikesMachineState
        assertThat(likesState.goal).isEqualTo(42)
        assertThat(machine.status).isEqualTo(MachineStatus.RUNNING)
        // Should NOT have replaced subject's DID with form's did parameter
        assertThat(likesState.did).isEqualTo("did:plc:qqk2pgu2uyqnbdp5jldvb3f2")
    }
}
