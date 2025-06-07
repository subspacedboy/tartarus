package club.subjugated.overlord_exe.storage

import club.subjugated.overlord_exe.models.BSkyUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BSkyUserRepository : JpaRepository<BSkyUser, Long> {
    fun findByName(name : String) : BSkyUser
    fun findByDid(did : String) : BSkyUser?
    fun findByShareableToken(shareableToken: String): BSkyUser?
    fun findByHandle(handle : String) : BSkyUser?
}