package club.subjugated.overlord_exe.machines

import club.subjugated.overlord_exe.models.BSkyLikesMachineState
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractSession
import club.subjugated.overlord_exe.models.MachineStatus
import club.subjugated.overlord_exe.models.SessionMachine
import club.subjugated.overlord_exe.runtime.BotContext
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.util.TimeSource
import jakarta.validation.constraints.Positive
import org.springframework.stereotype.Component

data class BSkyLikesForm(
    @field:Positive
    var goal: Long = 10,
    var did: String? = "",
    var name: String = "",
)

@Component
class BSkyLikesMachine(
    private val timeSource: TimeSource,
    private val blueSkyService: BlueSkyService,
) : SessionMachineHandler<BSkyLikesMachineState> {

    override val machineType: String = "bsky_likes"
    override val displayName: String = "Bluesky Likes"
    override val templateName: String = "info/bskylikesform"

    override fun initialStatus(): MachineStatus = MachineStatus.NEEDS_INFO

    override fun createInitialState(session: ContractSession): BSkyLikesMachineState {
        return BSkyLikesMachineState(
            did = session.subjectDid,
            goal = 100,
            likesSoFar = 0,
            createdAt = timeSource.nowInUtc(),
        )
    }

    override fun createInfoForm(token: String): Any {
        return BSkyLikesForm(name = token)
    }

    override fun applyInfoParams(
        machine: SessionMachine,
        state: BSkyLikesMachineState,
        params: Map<String, String>,
    ) {
        state.goal = params["goal"]?.toLongOrNull() ?: state.goal
        state.receivedFormData = true
        machine.status = MachineStatus.RUNNING
        machine.state = state
    }

    override fun onTick(
        ctx: BotContext,
        session: ContractSession,
        contract: Contract,
        machine: SessionMachine,
        state: BSkyLikesMachineState,
    ) {
        if (machine.status != MachineStatus.RUNNING) return

        if (state.receivedFormData) {
            state.receivedFormData = false
            if (state.likesSoFar >= state.goal) {
                machine.status = MachineStatus.SATISFIED
            }
            machine.state = state
            return
        }

        var totalLikes = 0L
        val since = state.createdAt ?: session.createdAt ?: timeSource.nowInUtc()
        blueSkyService.getAuthorFeedFromTime(state.did, since) { post ->
            blueSkyService.traceThread(post.uri!!) { post2 ->
                if (post2.author?.did == state.did && post2.embed?.asImages != null) {
                    totalLikes += (post2.likeCount ?: 0).toLong()
                }
            }
        }
        state.likesSoFar = totalLikes
        if (state.likesSoFar >= state.goal) {
            machine.status = MachineStatus.SATISFIED
        }
        machine.state = state
    }
}
