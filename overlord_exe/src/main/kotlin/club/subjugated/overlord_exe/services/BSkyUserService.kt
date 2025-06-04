package club.subjugated.overlord_exe.services

import club.subjugated.overlord_exe.models.BSkyUser
import club.subjugated.overlord_exe.storage.BSkyUserRepository
import club.subjugated.overlord_exe.util.TimeSource
import org.springframework.stereotype.Service

@Service
class BSkyUserService(
    private val bSkyUserRepository: BSkyUserRepository,
    private val timeSource: TimeSource
) {
    fun findOrCreateByDid(did: String): BSkyUser {
        return bSkyUserRepository.findByDid(did)
            ?: bSkyUserRepository.save(
                BSkyUser(
                    did = did,
                    shareableToken = "s-4UD7G60",
                    createdAt = timeSource.nowInUtc(),
                    updatedAt = timeSource.nowInUtc()
                )
            )
    }

    fun save(bSkyUser: BSkyUser) : BSkyUser {
        bSkyUser.updatedAt = timeSource.nowInUtc()
        return bSkyUserRepository.save(bSkyUser)
    }
}