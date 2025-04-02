package club.subjugated.tartarus_coordinator.models

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
class AuthorSession(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId(),
    var publicKey: String,
    //    var sessionToken : String?,

    @OneToMany(mappedBy = "id", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var contracts: MutableList<Contract> = mutableListOf(),
    @OneToMany(mappedBy = "id", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var knownTokens: MutableList<KnownToken> = mutableListOf(),
    @OneToMany(mappedBy = "id", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var commands: MutableList<Command> = mutableListOf(),
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) {
    companion object {
        fun generateId(): String {
            return club.subjugated.tartarus_coordinator.util.generateId("as-")
        }
    }
}
