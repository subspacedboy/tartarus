package club.subjugated.overlord_exe.statemachines.bsky_crowd_time

import club.subjugated.overlord_exe.cli.LikeRecord
import club.subjugated.overlord_exe.cli.RepostRecord
import club.subjugated.overlord_exe.events.RequestInfo
import club.subjugated.overlord_exe.models.StateMachine
import club.subjugated.overlord_exe.models.StateMachineState
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.UrlService
import club.subjugated.overlord_exe.statemachines.ContextProvider
import club.subjugated.overlord_exe.statemachines.bsky_likes.BSkyLikesStates
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.formatDuration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.temporal.ChronoUnit

@Service
class BSkyCrowdTimeStateMachine(
    private val bSkyCrowdTimeStateMachineContextRepository: BSkyCrowdTimeStateMachineContextRepository,
    private val timeSource: TimeSource,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val blueSkyService: BlueSkyService,
    private val urlService: UrlService,

) : ContextProvider<BSkyCrowdTimeForm, BSkyCrowdTimeStateMachineContext>{
    override fun createContext(
        stateMachineId: Long,
        form: BSkyCrowdTimeForm
    ): BSkyCrowdTimeStateMachineContext {
        val record = BSkyCrowdTimeStateMachineContext(
            stateMachineId = stateMachineId,
            subjectDid = form.subjectDid ?: "",
            createdAt = timeSource.nowInUtc(),
            updatedAt = timeSource.nowInUtc()
        )
        saveContext(record)
        return record
    }

    override fun findByStateMachineId(stateMachineId: Long): BSkyCrowdTimeStateMachineContext {
        return bSkyCrowdTimeStateMachineContextRepository.findByStateMachineId(stateMachineId)
    }

    fun findByName(name : String) : BSkyCrowdTimeStateMachineContext {
        return bSkyCrowdTimeStateMachineContextRepository.findByName(name)
    }

    override fun saveContext(ctx: BSkyCrowdTimeStateMachineContext): BSkyCrowdTimeStateMachineContext {
        return bSkyCrowdTimeStateMachineContextRepository.save(ctx)
    }

    override fun getInitialState(): String {
        return BSkyCrowdTimeState.CREATED.toString()
    }

    override fun process(
        stateMachine: StateMachine,
        ctx: BSkyCrowdTimeStateMachineContext
    ) {
        val state = BSkyCrowdTimeState.valueOf(stateMachine.currentState!!)
        when(state) {
            BSkyCrowdTimeState.UNSPECIFIED -> {throw IllegalStateException()
            }
            BSkyCrowdTimeState.CREATED -> handleCreated(stateMachine)
            BSkyCrowdTimeState.NEEDS_INFO -> { // Noop
            }
            BSkyCrowdTimeState.WAITING -> {
                if(ctx.receivedFormData) {
                    handlePostingNotice(stateMachine)
                }
            }
            BSkyCrowdTimeState.OPEN_POSTED -> handleOpenIsPosted(stateMachine)
            BSkyCrowdTimeState.IN_OPEN -> handleInOpen(stateMachine)
            BSkyCrowdTimeState.OPEN_CLOSED -> handleOpenClosed(stateMachine)
            BSkyCrowdTimeState.COMPLETE -> {}
        }
    }

    // Specific states

    private fun handleCreated(stateMachine: StateMachine) {
        applicationEventPublisher.publishEvent(RequestInfo(
            source = this,
            stateMachine = stateMachine,
            formClass = BSkyCrowdTimeForm::class.qualifiedName!!
        ))

        stateMachine.currentState = BSkyCrowdTimeState.WAITING.toString()
    }

    private fun handlePostingNotice(stateMachine: StateMachine) {
        val record = stateMachine.context as BSkyCrowdTimeStateMachineContext

        val subjectHandle = blueSkyService.resolveDidToHandle(stateMachine.bskyUser!!.did)

        val openPeriod = formatDuration(record.openPeriodAmount, record.openPeriodUnit)
        val likePeriod = formatDuration(record.perLikeAdd, record.perLikeAddUnit)
        val repostPeriod = formatDuration(record.perRepostAdd, record.perRepostAddUnit)

        val infoUrl = urlService.generateUrl("bsky_crowd_time/status/${record.name}")

        val announcement = """
            $subjectHandle has been locked. For the next $openPeriod after $subjectHandle reposts this, you may like and repost this notice.
            1 like = $likePeriod
            1 repost = $repostPeriod
            
            More Information: $infoUrl
        """.trimIndent()

        val uri = blueSkyService.post(announcement)
        record.noticeUri = uri
        saveContext(record)
        stateMachine.currentState = BSkyCrowdTimeState.OPEN_POSTED.toString()
    }

    private fun handleOpenIsPosted(stateMachine: StateMachine) {
        val record = stateMachine.context as BSkyCrowdTimeStateMachineContext

        // We need to see if the notice has been reposted
        val reposts = blueSkyService.getReposts(record.noticeUri)
        if(reposts.any {it.did == record.subjectDid}) {
            record.repostedNoticeAt = timeSource.nowInUtc()
            record.hasRepostedNotice = true
            val openPeriodDuration = Duration.of(record.openPeriodAmount.toLong(), ChronoUnit.valueOf(record.openPeriodUnit))
            record.openEndsAt = record.repostedNoticeAt!!.plus(openPeriodDuration)
            saveContext(record)

            stateMachine.currentState = BSkyCrowdTimeState.IN_OPEN.toString()
        }
    }

    private fun handleInOpen(stateMachine: StateMachine) {
        // Now we periodically recalculate the end-time and wait for the time
        // to elapse.
        val record = stateMachine.context as BSkyCrowdTimeStateMachineContext
        val openDuration = Duration.of(record.openPeriodAmount.toLong(), ChronoUnit.valueOf(record.openPeriodUnit))

        val totalLikes = HashSet<LikeRecord>()
        val totalReposts = HashSet<RepostRecord>()

        blueSkyService.traceThread(record.noticeUri) {
                post ->
            val likes = blueSkyService.getLikes(post.uri!!)
            likes.forEach {
                totalLikes.add(LikeRecord(it.actor.did))
            }

            val reposts = blueSkyService.getReposts(post.uri!!)
            reposts.forEach {
                // Exclude the author's mandatory repost.
                if(it.did != record.subjectDid) {
                    totalReposts.add(RepostRecord(it.did))
                }
            }
        }

        val likeTime = Duration.of(totalLikes.size.toLong() * record.perLikeAdd, ChronoUnit.valueOf(record.perLikeAddUnit))
        val repostTime = Duration.of(totalReposts.size.toLong() * record.perRepostAdd, ChronoUnit.valueOf(record.perRepostAddUnit))

        // TODO: make configurable
        val minPeriod = Duration.of(1L, ChronoUnit.HOURS)

        val newTime = record.repostedNoticeAt!!.plus(likeTime).plus(repostTime).plus(minPeriod)
        record.endsAt = newTime

        if(record.repostedNoticeAt!!.plus(openDuration) < timeSource.nowInUtc()) {
            stateMachine.currentState = BSkyCrowdTimeState.OPEN_CLOSED.toString()
        }
        saveContext(record)
    }

    private fun handleOpenClosed(stateMachine: StateMachine) {
        val record = stateMachine.context as BSkyCrowdTimeStateMachineContext

        if(timeSource.nowInUtc() > record.endsAt) {
            stateMachine.currentState = BSkyCrowdTimeState.COMPLETE.toString()
            stateMachine.state = StateMachineState.COMPLETE
        }

    }
}