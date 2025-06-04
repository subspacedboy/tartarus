package club.subjugated.overlord_exe.models

import club.subjugated.overlord_exe.util.generateId
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "bsky_user")
class BSkyUser(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId("bsu-"),
    var did: String,
    var handle: String? = "",
    var convoId: String? = "",
    var shareableToken: String? = "",
    var doNotContact: Boolean = false,
    @OneToMany(mappedBy = "bskyUser", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var infoRequests: MutableList<InfoRequest> = mutableListOf(),

    @OneToMany(mappedBy = "bskyUser", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var stateMachines: MutableList<StateMachine> = mutableListOf(),
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) {


}