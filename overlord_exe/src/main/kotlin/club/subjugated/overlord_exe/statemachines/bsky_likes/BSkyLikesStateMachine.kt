package club.subjugated.overlord_exe.statemachines.bsky_likes

import club.subjugated.overlord_exe.events.RequestInfo
import club.subjugated.overlord_exe.models.StateMachine
import club.subjugated.overlord_exe.models.StateMachineState
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.statemachines.ContextProvider
import club.subjugated.overlord_exe.util.TimeSource
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class BSkyLikesStateMachine(
    private val timeSource: TimeSource,
    private val bSkyLikesStateMachineContextRepository: BSkyLikesStateMachineContextRepository,
    private val blueSkyService: BlueSkyService,
    private val applicationEventPublisher: ApplicationEventPublisher,
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
        return BSkyLikesStates.NEEDS_INFO.toString()
    }

    override fun findByStateMachineId(stateMachineId: Long): BSkyLikesStateMachineContext {
        return bSkyLikesStateMachineContextRepository.findByStateMachineId(stateMachineId)
    }

    override fun process(stateMachine: StateMachine, ctx: BSkyLikesStateMachineContext) {
        val state = BSkyLikesStates.valueOf(stateMachine.currentState!!)
        when(state) {
            BSkyLikesStates.UNSPECIFIED -> TODO()
            BSkyLikesStates.NEEDS_INFO -> stateNeedsInfo(stateMachine)
            BSkyLikesStates.WAITING -> {
                if(ctx.receivedFormData) {
                    stateMachine.currentState = BSkyLikesStates.IN_PROGRESS.toString()
                }
            }
            BSkyLikesStates.IN_PROGRESS -> stateInProgress(stateMachine)
            BSkyLikesStates.COMPLETE -> {}
        }
    }

    fun stateNeedsInfo(stateMachine: StateMachine) {
        val record = findByStateMachineId(stateMachine.id)

        applicationEventPublisher.publishEvent(RequestInfo(
            source = this,
            stateMachine = stateMachine,
            formClass = BSkyLikesForm::class.qualifiedName!!
        ))

        stateMachine.currentState = BSkyLikesStates.WAITING.toString()
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