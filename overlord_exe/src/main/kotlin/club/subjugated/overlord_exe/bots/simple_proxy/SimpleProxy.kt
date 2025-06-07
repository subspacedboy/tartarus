package club.subjugated.overlord_exe.bots.simple_proxy

import club.subjugated.overlord_exe.bots.superbot.SuperBotRecordState
import club.subjugated.overlord_exe.models.BSkyUser
import club.subjugated.overlord_exe.util.generateId
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.time.OffsetDateTime

@Entity
class SimpleProxy(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId("sp-"),
    @Enumerated(EnumType.STRING) var state: SimpleProxyState = SimpleProxyState.UNSPECIFIED,
    var contractSerialNumber: Int,
    var contractId: Long? = null,
    var isPublic: Boolean = false,
    @ManyToOne @JoinColumn(name = "bsky_user_id") val bskyUser: BSkyUser,
    @ManyToOne @JoinColumn(name = "key_holder_bsky_user_id") val keyHolderBskyUser: BSkyUser,

    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var acceptedAt: OffsetDateTime? = null,
) {
}