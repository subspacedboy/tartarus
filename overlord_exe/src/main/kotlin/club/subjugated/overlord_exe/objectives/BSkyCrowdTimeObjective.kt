package club.subjugated.overlord_exe.objectives

import club.subjugated.overlord_exe.cli.LikeRecord
import club.subjugated.overlord_exe.cli.RepostRecord
import club.subjugated.overlord_exe.models.BSkyCrowdTimeObjectiveState
import club.subjugated.overlord_exe.models.BotEngagement
import club.subjugated.overlord_exe.models.CrowdTimePhase
import club.subjugated.overlord_exe.models.ObjectiveStatus
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.UrlService
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.formatDuration
import jakarta.validation.constraints.Positive
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.temporal.ChronoUnit

data class BSkyCrowdTimeForm(
    @field:Positive
    var perLikeAdd: Long = 1,
    var perLikeAddUnit: String = "MINUTES",

    @field:Positive
    var perRepostAdd: Long = 1,
    var perRepostAddUnit: String = "HOURS",

    @field:Positive
    var openPeriodAmount: Long = 1,
    var openPeriodUnit: String = "HOURS",

    var subjectDid: String? = "",
    var name: String = "",
)

@Component
class BSkyCrowdTimeObjective(
    private val timeSource: TimeSource,
    private val blueSkyService: BlueSkyService,
    private val urlService: UrlService,
) : Objective<BSkyCrowdTimeObjectiveState> {

    override val objectiveId: String = "bsky_crowd_time"
    override val templateName: String = "info/bskycrowdtimeform"

    override fun createInitialState(subjectDid: String): BSkyCrowdTimeObjectiveState {
        return BSkyCrowdTimeObjectiveState(
            subjectDid = subjectDid,
            phase = CrowdTimePhase.WAITING,
            createdAt = timeSource.nowInUtc(),
        )
    }

    override fun createEmptyForm(token: String): Any {
        return BSkyCrowdTimeForm(name = token)
    }

    override fun applyFormParams(state: BSkyCrowdTimeObjectiveState, params: Map<String, String>) {
        state.perLikeAdd = params["perLikeAdd"]?.toLongOrNull() ?: 1L
        state.perLikeAddUnit = params["perLikeAddUnit"] ?: "MINUTES"
        state.perRepostAdd = params["perRepostAdd"]?.toLongOrNull() ?: 1L
        state.perRepostAddUnit = params["perRepostAddUnit"] ?: "HOURS"
        state.openPeriodAmount = params["openPeriodAmount"]?.toLongOrNull() ?: 1L
        state.openPeriodUnit = params["openPeriodUnit"] ?: "HOURS"
        state.receivedFormData = true
        state.phase = CrowdTimePhase.WAITING
    }

    override fun evaluate(state: BSkyCrowdTimeObjectiveState, engagement: BotEngagement) {
        when (state.phase) {
            CrowdTimePhase.UNSPECIFIED,
            CrowdTimePhase.CREATED,
            CrowdTimePhase.NEEDS_INFO,
            CrowdTimePhase.WAITING -> {
                if (state.receivedFormData) {
                    postNotice(state)
                }
            }
            CrowdTimePhase.OPEN_POSTED -> checkNoticeReposted(state)
            CrowdTimePhase.IN_OPEN -> updateOpenPeriodTime(state)
            CrowdTimePhase.OPEN_CLOSED -> checkExpired(state)
            CrowdTimePhase.COMPLETE -> {
                state.status = ObjectiveStatus.COMPLETE
            }
        }
    }

    private fun postNotice(state: BSkyCrowdTimeObjectiveState) {
        val subjectHandle = blueSkyService.resolveDidToHandle(state.subjectDid)
        val openPeriod = formatDuration(state.openPeriodAmount, state.openPeriodUnit)
        val likePeriod = formatDuration(state.perLikeAdd, state.perLikeAddUnit)
        val repostPeriod = formatDuration(state.perRepostAdd, state.perRepostAddUnit)
        val infoUrl = urlService.generateUrl("bsky_crowd_time/status/${state.name}")

        val announcement = """
            $subjectHandle has been locked. For the next $openPeriod after $subjectHandle reposts this, you may like and repost this notice.
            1 like = $likePeriod
            1 repost = $repostPeriod
            
            More Information: $infoUrl
        """.trimIndent()

        state.noticeUri = blueSkyService.post(announcement)
        state.phase = CrowdTimePhase.OPEN_POSTED
    }

    private fun checkNoticeReposted(state: BSkyCrowdTimeObjectiveState) {
        val reposts = blueSkyService.getReposts(state.noticeUri)
        if (reposts.any { it.did == state.subjectDid }) {
            state.repostedNoticeAt = timeSource.nowInUtc()
            state.hasRepostedNotice = true
            val openPeriodDuration = Duration.of(
                state.openPeriodAmount,
                ChronoUnit.valueOf(state.openPeriodUnit),
            )
            state.openEndsAt = state.repostedNoticeAt!!.plus(openPeriodDuration)
            state.phase = CrowdTimePhase.IN_OPEN
        }
    }

    private fun updateOpenPeriodTime(state: BSkyCrowdTimeObjectiveState) {
        val openDuration = Duration.of(
            state.openPeriodAmount,
            ChronoUnit.valueOf(state.openPeriodUnit),
        )

        val totalLikes = HashSet<LikeRecord>()
        val totalReposts = HashSet<RepostRecord>()

        blueSkyService.traceThread(state.noticeUri) { post ->
            val likes = blueSkyService.getLikes(post.uri!!)
            likes.forEach { totalLikes.add(LikeRecord(it.actor.did)) }

            val reposts = blueSkyService.getReposts(post.uri!!)
            reposts.forEach {
                if (it.did != state.subjectDid) {
                    totalReposts.add(RepostRecord(it.did))
                }
            }
        }

        val likeTime = Duration.of(
            totalLikes.size.toLong() * state.perLikeAdd,
            ChronoUnit.valueOf(state.perLikeAddUnit),
        )
        val repostTime = Duration.of(
            totalReposts.size.toLong() * state.perRepostAdd,
            ChronoUnit.valueOf(state.perRepostAddUnit),
        )
        val minPeriod = Duration.of(1L, ChronoUnit.HOURS)

        state.endsAt = state.repostedNoticeAt!!.plus(likeTime).plus(repostTime).plus(minPeriod)

        if (state.repostedNoticeAt!!.plus(openDuration) < timeSource.nowInUtc()) {
            state.phase = CrowdTimePhase.OPEN_CLOSED
        }
    }

    private fun checkExpired(state: BSkyCrowdTimeObjectiveState) {
        val endsAt = state.endsAt ?: return
        if (timeSource.nowInUtc() > endsAt) {
            state.phase = CrowdTimePhase.COMPLETE
            state.status = ObjectiveStatus.COMPLETE
        }
    }
}
