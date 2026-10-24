package club.subjugated.tartarus_coordinator.models

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import java.time.OffsetDateTime
import java.util.*

@Entity
class Bot(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId(),
    @Enumerated(EnumType.STRING) var state: BotState = BotState.ACTIVE,
    var publicKey: String,
    @Transient var clearTextPassword: String? = null,
    var password: String? = "",
    var salt: String? = "",
    var description: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    var authorSession: AuthorSession? = null,
    @Column(name = "internal_name") var internalName: String? = null,
    @Column(name = "key_version") var keyVersion: Int = 1,
    @OneToMany(mappedBy = "bot", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var messages: MutableList<Message> = mutableListOf(),
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) : PublicKeyProvider {
    companion object {
        fun generateId(): String {
            return club.subjugated.tartarus_coordinator.util.generateId("b-")
        }
    }

    override fun decodePublicKey(): ByteArray {
        return Base64.getDecoder().decode(this.publicKey)
    }
}
