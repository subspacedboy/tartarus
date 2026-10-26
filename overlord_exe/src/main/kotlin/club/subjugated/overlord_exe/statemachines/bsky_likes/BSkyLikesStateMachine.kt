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

    override val objectiveId: String = "bsky_likes"
    override val formTypeName: String = BSkyLikesForm::class.qualifiedName!!
    override val templateName: String = "info/bskylikesform"

    override fun createEmptyForm(requestName: String): BSkyLikesForm {
        return BSkyLikesForm(name = requestName)
    }

    override fun bindForm(params: Map<String, String>): BSkyLikesForm {
        return BSkyLikesForm(
            name = params["name"] ?: "",
            did = params["did"] ?: "",
            goal = params["goal"]?.toLongOrNull() ?: 10L,
        )
    }

    override fun createContext(stateMachineId: Long, form: BSkyLikesForm): BSkyLikesStateMachineContext {
        val bskyContext = BSkyLikesStateMachineContext(
            stateMachineId = stateMachineId,
            goal = form.goal,
            likesSoFar = 0,
            did = form.did ?: "",
            createdAt = timeSource.nowInUtc(),
            updatedAt = timeSource.nowInUtc(),
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
        when (state) {
            BSkyLikesStates.UNSPECIFIED -> throw IllegalStateException("UNSPECIFIED BSkyLikes state")
            BSkyLikesStates.NEEDS_INFO -> stateNeedsInfo(stateMachine)
            BSkyLikesStates.WAITING -> {
                if (ctx.receivedFormData) {
                    stateMachine.currentState = BSkyLikesStates.IN_PROGRESS.toString()
                }
            }
            BSkyLikesStates.IN_PROGRESS -> stateInProgress(stateMachine)
            BSkyLikesStates.COMPLETE -> {}
        }
    }

    private fun stateNeedsInfo(stateMachine: StateMachine) {
        applicationEventPublisher.publishEvent(
            RequestInfo(
                source = this,
                stateMachine = stateMachine,
                formClass = formTypeName,
            )
        )
        stateMachine.currentState = BSkyLikesStates.WAITING.toString()
    }

    private fun stateInProgress(stateMachine: StateMachine) {
        val ctx: BSkyLikesStateMachineContext = stateMachine.context!! as BSkyLikesStateMachineContext

        var totalLikes = 0L
        blueSkyService.getAuthorFeedFromTime(ctx.did, ctx.createdAt!!) { post ->
            blueSkyService.traceThread(post.uri!!) { post2 ->
                if (post2.author?.did == ctx.did && post2.embed?.asImages != null) {
                    totalLikes += post2.likeCount!!
                }
            }
        }
        ctx.likesSoFar = totalLikes
        if (ctx.likesSoFar >= ctx.goal) {
            stateMachine.currentState = BSkyLikesStates.COMPLETE.toString()
            stateMachine.state = StateMachineState.COMPLETE
        }
    }
}
