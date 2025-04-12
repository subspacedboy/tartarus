package club.subjugated.overlord_exe.bots.announcer

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AnnouncerRecordRepository : JpaRepository<AnnouncerRecord, Long> {
    fun findByToken(token: String) : AnnouncerRecord?
}