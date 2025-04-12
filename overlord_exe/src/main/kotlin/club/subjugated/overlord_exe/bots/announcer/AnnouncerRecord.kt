package club.subjugated.overlord_exe.bots.announcer

import club.subjugated.overlord_exe.util.generateId
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.OffsetDateTime

@Entity
class AnnouncerRecord(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId("ar-"),
    var token: String,
    var did: String,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
) {

}