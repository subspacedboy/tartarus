package club.subjugated.overlord_exe.storage

import club.subjugated.overlord_exe.models.BSkyLike
import club.subjugated.overlord_exe.models.BSkyRepost
import org.springframework.data.jpa.repository.JpaRepository

interface BSkyRepostRepository : JpaRepository<BSkyRepost, Long> {
    fun findByRecordIdAndRecordType(recordId : Long, recordType : String) : List<BSkyRepost>
}