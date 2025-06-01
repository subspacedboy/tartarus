package club.subjugated.overlord_exe.bots.superbot

import club.subjugated.overlord_exe.bots.bsky_selflock.BSkySelfLockRecordState
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
class SuperBotRecord(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId("sb-"),
    @Enumerated(EnumType.STRING) var state: SuperBotRecordState = SuperBotRecordState.UNSPECIFIED,
    var shareableToken : String = "",
    var contractSerialNumber: Int,
    var contractId: Long? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
//    @JsonFormat(shape = JsonFormat.Shape.STRING) var acceptedAt: OffsetDateTime? = null,
//    @JsonFormat(shape = JsonFormat.Shape.STRING) var openEndsAt: OffsetDateTime? = null,
//    @JsonFormat(shape = JsonFormat.Shape.STRING) var endsAt: OffsetDateTime? = null,
    var did: String,
    var convoId: String,
) {
}