package club.subjugated.overlord_exe.bots.superbot

import club.subjugated.overlord_exe.statemachines.ContextForm
import club.subjugated.overlord_exe.statemachines.bsky_crowd_time.BSkyCrowdTimeForm
import club.subjugated.overlord_exe.statemachines.bsky_crowd_time.BSkyCrowdTimeStateMachine
import club.subjugated.overlord_exe.statemachines.bsky_likes.BSkyLikesForm
import club.subjugated.overlord_exe.statemachines.bsky_likes.BSkyLikesStateMachine
import club.subjugated.overlord_exe.util.generateId
import com.fasterxml.jackson.annotation.JsonFormat
import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.annotations.Type
import java.time.OffsetDateTime

@Entity
class SuperBotRecord(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId("sb-"),
    @Enumerated(EnumType.STRING) var state: SuperBotRecordState = SuperBotRecordState.UNSPECIFIED,
    var shareableToken : String? = "",
    var contractSerialNumber: Int,
    var contractId: Long? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var acceptedAt: OffsetDateTime? = null,
    var did: String,
    var convoId: String,
    @Type(value = JsonType::class)
    var configuration: SuperBotConfig = SuperBotConfig()
) {
    fun chooseObjective() : Pair<ContextForm, String> {
        val objective = configuration.objectives.random()
        when(objective) {
            "bsky_likes" -> {
                val form = BSkyLikesForm(
                    did = did,
                    goal = 100
                )
                return Pair(form, BSkyLikesStateMachine::class.qualifiedName!!)
            }
            "bsky_crowd_time" -> {
                val form = BSkyCrowdTimeForm(
                    name = "",
                    subjectDid = did
                )
                return Pair(form, BSkyCrowdTimeStateMachine::class.qualifiedName!!)
            }
            else -> throw IllegalStateException()
        }
    }
}