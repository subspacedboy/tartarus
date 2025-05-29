package club.subjugated.overlord_exe.models

import club.subjugated.overlord_exe.util.generateId
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.OffsetDateTime

@Entity
class BSkyRepost(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId("rp-"),
    var recordId: Int = 0,
    @Column(length = 25)
    var recordType: String = "",
    @Column(length = 255, nullable = false)
    var atUri: String = "",
    @Column(length = 255, nullable = false)
    var did: String = "",
    @Column(length = 255, nullable = false)
    var handle: String = "",
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) {
}