package club.subjugated.overlord_exe.storage

import club.subjugated.overlord_exe.models.InfoRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface InfoRequestRepository : JpaRepository<InfoRequest, Long> {
    fun findByName(name : String) : InfoRequest
}