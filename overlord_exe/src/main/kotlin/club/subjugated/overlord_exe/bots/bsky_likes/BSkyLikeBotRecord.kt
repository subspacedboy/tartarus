package club.subjugated.overlord_exe.bots.bsky_likes

import club.subjugated.overlord_exe.util.generateId
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.OffsetDateTime

@Entity
class BSkyLikeBotRecord(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId("bslb-"),
    var contractSerialNumber: Int,
    var contractId: Long? = null,
    var goal: Long,
    var likesSoFar: Long,
    var did: String,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var acceptedAt: OffsetDateTime? = null,
    var completed : Boolean = false
) {
}