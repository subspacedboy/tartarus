package club.subjugated.overlord_exe.bots.bsky_selflock

import club.subjugated.overlord_exe.util.generateId
import jakarta.persistence.Entity

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
class BSkySelfLockBotRecord(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId("bsslr-"),
    @Enumerated(EnumType.STRING) var state: BSkySelfLockRecordState = BSkySelfLockRecordState.UNSPECIFIED,
    var shareableToken : String = "",
    var contractSerialNumber: Int,
    var contractId: Long? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var acceptedAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var openEndsAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var endsAt: OffsetDateTime? = null,
    var durationAmount: Int = 0,
    var durationUnit: String = "MINUTES",
    var perLikeAdd: Int = 0,
    var perLikeAddUnit: String = "MINUTES",
    var perRepostAdd: Int = 0,
    var perRepostAddUnit: String = "MINUTES",
    var openPeriodAmount: Int = 1,
    var openPeriodUnit: String = "HOURS",
    var noticeUri : String = "",
    var hasRepostedNotice: Boolean = false,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var repostedNoticeAt: OffsetDateTime? = null,
    var did: String,
    var convoId: String,
    var completed: Boolean? = null
)