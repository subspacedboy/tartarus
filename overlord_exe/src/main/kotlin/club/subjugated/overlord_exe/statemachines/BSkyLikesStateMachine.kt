package club.subjugated.overlord_exe.statemachines

import club.subjugated.overlord_exe.models.StateMachine
import club.subjugated.overlord_exe.models.StateMachineState
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.StateMachineService
import club.subjugated.overlord_exe.util.TimeSource
import org.springframework.stereotype.Service

data class BSkyLikesForm (
    var did: String,
    var goal: Long
) : ContextForm

@Service
class BSkyLikesStateMachine(
    val timeSource: TimeSource,
    val bSkyLikesStateMachineContextRepository: BSkyLikesStateMachineContextRepository,
    var blueSkyService: BlueSkyService
) : ContextProvider<BSkyLikesForm, BSkyLikesStateMachineContext> {

    override fun createContext(stateMachineId : Long, form: BSkyLikesForm): BSkyLikesStateMachineContext {
        val bskyContext = BSkyLikesStateMachineContext(
            stateMachineId = stateMachineId,
            goal = form.goal,
            likesSoFar = 0,
            did = form.did,
            createdAt = timeSource.nowInUtc(),
            updatedAt = timeSource.nowInUtc()
        )
        bSkyLikesStateMachineContextRepository.save(bskyContext)
        return bskyContext
    }

    override fun saveContext(ctx: BSkyLikesStateMachineContext): BSkyLikesStateMachineContext {
        bSkyLikesStateMachineContextRepository.save(ctx)
        return ctx
    }

    override fun getInitialState(): String {
        return BSkyLikesStates.IN_PROGRESS.toString()
    }

    override fun findByStateMachineId(stateMachineId: Long): BSkyLikesStateMachineContext {
        return bSkyLikesStateMachineContextRepository.findByStateMachineId(stateMachineId)
    }

//    fun loadContext(stateMachine: StateMachine) : StateMachine {
//        val ctx = bSkyLikesStateMachineContextRepository.findByStateMachineId(stateMachine.id)
//        stateMachine.context = ctx
//        return stateMachine
//    }

    override fun process(stateMachine: StateMachine, ctx: BSkyLikesStateMachineContext) {
        val state = BSkyLikesStates.valueOf(stateMachine.currentState!!)
        when(state) {
            BSkyLikesStates.UNSPECIFIED -> TODO()
            BSkyLikesStates.IN_PROGRESS -> stateInProgress(stateMachine)
            BSkyLikesStates.COMPLETE -> {}
        }
    }

    fun stateInProgress(stateMachine: StateMachine) {
        val ctx : BSkyLikesStateMachineContext = stateMachine.context!! as BSkyLikesStateMachineContext

        var totalLikes = 0L
        blueSkyService.getAuthorFeedFromTime(ctx.did, ctx.createdAt!!) { post ->
            blueSkyService.traceThread(post.uri!!) { post2 ->
                if(post2.author?.did == ctx.did) {
                    if(post2.embed?.asImages != null) {
                        val imageEmbed = post2.embed!!.asImages!!
                        totalLikes += post2.likeCount!!
                    }
                }
            }
        }
        ctx.likesSoFar = totalLikes
        if(ctx.likesSoFar >= ctx.goal) {
            stateMachine.currentState = BSkyLikesStates.COMPLETE.toString()
            stateMachine.state = StateMachineState.COMPLETE
        }
    }
}