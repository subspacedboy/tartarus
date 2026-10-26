package club.subjugated.overlord_exe.objectives

import club.subjugated.overlord_exe.models.BSkyLikesObjectiveState
import club.subjugated.overlord_exe.models.BotEngagement
import club.subjugated.overlord_exe.models.ObjectiveStatus
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
class BSkyLikesObjective(
    private val timeSource: TimeSource,
    private val blueSkyService: BlueSkyService,
) : Objective<BSkyLikesObjectiveState> {

    override val objectiveId: String = "bsky_likes"
    override val templateName: String = "info/bskylikesform"

    override fun createInitialState(subjectDid: String): BSkyLikesObjectiveState {
        return BSkyLikesObjectiveState(
            did = subjectDid,
            goal = 100,
            likesSoFar = 0,
            createdAt = timeSource.nowInUtc(),
        )
    }

    override fun createEmptyForm(token: String): Any {
        return BSkyLikesForm(name = token)
    }

    override fun applyFormParams(state: BSkyLikesObjectiveState, params: Map<String, String>) {
        // Intentionally preserve state.did (subject's DID) and only update the goal from form submission
        state.goal = params["goal"]?.toLongOrNull() ?: state.goal
        state.receivedFormData = true
    }

    override fun evaluate(state: BSkyLikesObjectiveState, engagement: BotEngagement) {
        if (state.status == ObjectiveStatus.COMPLETE) return

        // On initial form submission, transition into active tracking; likes are counted on subsequent review ticks
        if (state.receivedFormData) {
            state.receivedFormData = false
            if (state.likesSoFar >= state.goal) {
                state.status = ObjectiveStatus.COMPLETE
            }
            return
        }

        var totalLikes = 0L
        val since = state.createdAt ?: engagement.createdAt ?: timeSource.nowInUtc()
        blueSkyService.getAuthorFeedFromTime(state.did, since) { post ->
            blueSkyService.traceThread(post.uri!!) { post2 ->
                if (post2.author?.did == state.did && post2.embed?.asImages != null) {
                    totalLikes += post2.likeCount!!
                }
            }
        }
        state.likesSoFar = totalLikes
        if (state.likesSoFar >= state.goal) {
            state.status = ObjectiveStatus.COMPLETE
        }
    }
}
