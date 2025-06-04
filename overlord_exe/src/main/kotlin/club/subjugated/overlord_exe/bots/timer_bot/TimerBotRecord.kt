package club.subjugated.overlord_exe.bots.timer_bot

import club.subjugated.overlord_exe.util.generateId
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.OffsetDateTime

@Entity
class TimerBotRecord(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId("tbr-"),
    @Enumerated(EnumType.STRING)
    var state : TimerBotRecordState = TimerBotRecordState.UNSPECIFIED,
    var contractSerialNumber: Int,
    var contractId: Long? = null,
    var isPublic: Boolean = false,
    var shareableToken : String = "",
    var timeAmount: Long = 0,
    var timeUnit: String = "minute",

    var isRandom: Boolean = false,
    var minDuration: Int = 0,
    var minDurationUnit: String = "MINUTES",
    var maxDuration: Int = 0,
    var maxDurationUnit: String = "MINUTES",

    var did: String? = "",
    var convoId: String? = "",
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var endsAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var acceptedAt: OffsetDateTime? = null,
    var completed : Boolean = false
) {
}