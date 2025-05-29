package club.subjugated.overlord_exe.storage

import club.subjugated.overlord_exe.models.BSkyLike
import org.springframework.data.jpa.repository.JpaRepository

interface BskyLikeRepository : JpaRepository<BSkyLike, Long> {
    fun findByRecordIdAndRecordType(recordId : Long, recordType : String) : List<BSkyLike>
}