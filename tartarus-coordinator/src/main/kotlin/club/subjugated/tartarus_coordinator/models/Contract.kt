package club.subjugated.tartarus_coordinator.models

import club.subjugated.fb.message.Command
import club.subjugated.fb.message.CommandType
import club.subjugated.fb.message.SignedMessage
import club.subjugated.tartarus_coordinator.models.LockSession.Companion.generateId
import com.fasterxml.jackson.annotation.JsonFormat
import com.google.flatbuffers.FlatBufferBuilder
import jakarta.persistence.*
import java.nio.ByteBuffer
import java.time.OffsetDateTime

@Entity
class Contract(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    var name: String = generateId(),
    var publicKey : String,
    var shareableToken : String?,
    @Enumerated(EnumType.STRING)
    var state : ContractState = ContractState.UNSPECIFIED,
    @ManyToOne
    @JoinColumn(name = "author_id")
    var authorSession: AuthorSession,
    var body : ByteArray? = null,


    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) {
    companion object {
        fun generateId() : String {
            return club.subjugated.tartarus_coordinator.util.generateId("c-")
        }
    }

//    fun toCommand() : ByteArray {
//        val builder = FlatBufferBuilder(1024)
//
//        val embeddedOffset = builder.createByteVector(this.body)
//
//        Command.startCommand(builder)
//        Command.addSignedMessage(builder, embeddedOffset)
//        Command.addCommandType(builder, CommandType.AcceptContract)
//        val commandOffset = Command.endCommand(builder)
//
//        builder.finish(commandOffset)
//        return builder.sizedByteArray()
//    }
}