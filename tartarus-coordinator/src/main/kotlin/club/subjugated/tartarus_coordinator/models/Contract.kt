package club.subjugated.tartarus_coordinator.models

import club.subjugated.fb.message.Bot
import club.subjugated.fb.message.Contract
import club.subjugated.fb.message.SignedMessage
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.*
import java.nio.ByteBuffer
import java.time.OffsetDateTime
import kotlin.jvm.Transient

@Entity
class Contract(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId(),
    var shareableToken: String?,
    @Enumerated(EnumType.STRING) var state: ContractState = ContractState.UNSPECIFIED,
    @ManyToOne @JoinColumn(name = "author_id") var authorSession: AuthorSession,
    @ManyToOne @JoinColumn(name = "lock_session_id") var lockSession: LockSession,
    var body: ByteArray? = null,
    var notes: String? = null,
    @OneToMany(mappedBy = "id", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var commands: MutableList<Command> = mutableListOf(),
    var nextCounter: Int = 0,
    var serialNumber: Int = 0,
    @Transient var lockState: Boolean? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) {
    companion object {
        fun generateId(): String {
            return club.subjugated.tartarus_coordinator.util.generateId("c-")
        }
    }

    fun getEmbeddedBots() : List<Bot> {
        val byteBuffer = ByteBuffer.wrap(body)

        // Parse the FlatBuffer object
        val sm = SignedMessage.getRootAsSignedMessage(byteBuffer)
        val contract = Contract()
        sm.payload(contract)

        val botList = mutableListOf<club.subjugated.fb.message.Bot>()
        for (i in 0 until contract.botsLength) {
            val bot = contract.bots(i) ?: continue
            println("Bot name: ${bot.name}")

            // Access permissions
            val permissions = bot.permissions
            if (permissions != null) {
                println("  Receive Events: ${permissions.receiveEvents}")
                println("  Can Unlock: ${permissions.canUnlock}")
                println("  Can Release: ${permissions.canRelease}")
            }

            botList.add(bot)
        }

        return botList
    }
}
