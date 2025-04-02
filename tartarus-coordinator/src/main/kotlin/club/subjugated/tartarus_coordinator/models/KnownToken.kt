package club.subjugated.tartarus_coordinator.models

import club.subjugated.tartarus_coordinator.models.Contract.Companion.generateId
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
class KnownToken(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId(),
    @Enumerated(EnumType.STRING) var state: KnownTokenState = KnownTokenState.UNSPECIFIED,
    @ManyToOne @JoinColumn(name = "author_id") var authorSession: AuthorSession,
    var notes: String = "",
    var shareableToken: String,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
) {
    companion object {
        fun generateId(): String {
            return club.subjugated.tartarus_coordinator.util.generateId("kt-")
        }
    }
}
