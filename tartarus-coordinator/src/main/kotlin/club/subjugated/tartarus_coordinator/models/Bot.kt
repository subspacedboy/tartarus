package club.subjugated.tartarus_coordinator.models

import club.subjugated.tartarus_coordinator.util.generateSalt
import club.subjugated.tartarus_coordinator.util.runSCryptWithCommonParams
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import org.bouncycastle.util.encoders.Base32
import java.time.OffsetDateTime
import java.util.*

@Entity
class Bot (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId(),
    @Enumerated(EnumType.STRING) var state: BotState = BotState.UNSPECIFIED,
    var publicKey: String,
    @Transient
    var clearTextPassword: String? = null,
    var password: String? = "",
    var salt: String? = "",
    var description: String,
    @OneToMany(mappedBy = "id", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var messages: MutableList<Message> = mutableListOf(),
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) {
    companion object {
        fun generateId(): String {
            return club.subjugated.tartarus_coordinator.util.generateId("b-")
        }
    }

    fun canReadMqtt(topic: String) : Boolean {
        return listOf("bots/inbox_api_${name}", "bots/inbox_events_${name}").contains(topic)
    }

    fun canWriteMqtt(topic: String) : Boolean {
        // Bots can always write to lock queues?
        if(topic.startsWith("locks/")) {
            return true
        }
        return listOf("status/${name}", "coordinator/inbox").contains(topic)
    }

    fun checkPassword(input : String) : Boolean {
        val salt = Base64.getDecoder().decode(this.salt)
        val derived = runSCryptWithCommonParams(input.toByteArray(), salt)
        return Base64.getEncoder().encodeToString(derived) == this.password
    }

    fun generatePassword() {
        val password = String(Base32.encode(generateSalt().sliceArray(0..9)))
        val salt = generateSalt()

        val derived = runSCryptWithCommonParams(password.toByteArray(), salt)

        this.clearTextPassword = password
        this.salt = Base64.getEncoder().encodeToString(salt)
        this.password = Base64.getEncoder().encodeToString(derived)
    }
}