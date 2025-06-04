package club.subjugated.overlord_exe.statemachines

import club.subjugated.overlord_exe.OverlordExeApplication
import club.subjugated.overlord_exe.integration.config.IntegrationTestConfig
import club.subjugated.overlord_exe.services.StateMachineService
import club.subjugated.overlord_exe.statemachines.bsky_likes.BSkyLikesForm
import club.subjugated.overlord_exe.statemachines.bsky_likes.BSkyLikesStateMachine
import club.subjugated.overlord_exe.statemachines.bsky_likes.BSkyLikesStateMachineContext
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.Commit
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import kotlin.test.Test

@SpringBootTest(classes = [OverlordExeApplication::class, IntegrationTestConfig::class])
@TestPropertySource(locations = ["classpath:application-test.properties"])
@Transactional
@Commit
class TestStateMachine {
    @Autowired lateinit var stateMachineService: StateMachineService

    @Test
    fun test() {
        val form = BSkyLikesForm(
            did = "did:plc:qqk2pgu2uyqnbdp5jldvb3f2",
            goal = 100
        )

        val sm = stateMachineService.createNewStateMachine(
            ownerName = "c-blahblah",
            providerClassName = BSkyLikesStateMachine::class.qualifiedName!!,
            form = form
        )

        stateMachineService.process(sm)

        assertThat(sm.currentState).isEqualTo("IN_PROGRESS")

        (sm.context as BSkyLikesStateMachineContext).goal = 0
        stateMachineService.process(sm)

        stateMachineService.save(sm)

        assertThat(sm.currentState).isEqualTo("COMPLETE")

        val sm2 = stateMachineService.findByName(sm.name)

        assertThat(sm.currentState).isEqualTo(sm2.currentState)
        assertThat(sm.context).isEqualTo(sm2.context)
    }
}