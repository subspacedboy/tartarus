package club.subjugated.overlord_exe.statemachines

import club.subjugated.overlord_exe.util.generateId
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "bsky_likes_sm_context")
class BSkyLikesStateMachineContext(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId("bslsmc-"),
    var stateMachineId: Long,
    var goal: Long,
    var likesSoFar: Long,
    var did: String,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) : Context