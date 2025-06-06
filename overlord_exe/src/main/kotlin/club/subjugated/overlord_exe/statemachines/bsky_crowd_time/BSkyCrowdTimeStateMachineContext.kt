package club.subjugated.overlord_exe.statemachines.bsky_crowd_time

import club.subjugated.overlord_exe.bots.bsky_selflock.BSkySelfLockRecordState
import club.subjugated.overlord_exe.statemachines.Context
import club.subjugated.overlord_exe.statemachines.ContextForm
import club.subjugated.overlord_exe.statemachines.bsky_likes.BSkyLikesForm
import club.subjugated.overlord_exe.util.generateId
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "bsky_crowd_time_sm_context")
class BSkyCrowdTimeStateMachineContext (
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId("bsctsmc-"),
//    @Enumerated(EnumType.STRING) var state: BSkyCrowdTimeState = BSkyCrowdTimeState.UNSPECIFIED,

    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var acceptedAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var openEndsAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var endsAt: OffsetDateTime? = null,

    var stateMachineId: Long,
    var perLikeAdd: Long = 1,
    var perLikeAddUnit: String = "MINUTES",
    var perRepostAdd: Long = 1,
    var perRepostAddUnit: String = "HOURS",
    var openPeriodAmount: Long = 1,
    var openPeriodUnit: String = "HOURS",

    var subjectDid: String = "",
    var noticeUri : String = "",
    var hasRepostedNotice: Boolean = false,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var repostedNoticeAt: OffsetDateTime? = null,

    @Transient var receivedFormData: Boolean = false,
) : Context {
    override fun receive(form: ContextForm) {
        val actualTypedForm = form as BSkyCrowdTimeForm

        this.perLikeAdd = actualTypedForm.perLikeAdd
        this.perLikeAddUnit = actualTypedForm.perLikeAddUnit

        this.perRepostAdd = actualTypedForm.perRepostAdd
        this.perRepostAddUnit = actualTypedForm.perRepostAddUnit

        this.openPeriodAmount = actualTypedForm.openPeriodAmount
        this.openPeriodUnit = actualTypedForm.openPeriodUnit

        this.receivedFormData = true
    }
}