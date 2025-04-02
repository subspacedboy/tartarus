package club.subjugated.tartarus_coordinator.services

import club.subjugated.tartarus_coordinator.models.LockSession
import club.subjugated.tartarus_coordinator.models.LockUserSession
import club.subjugated.tartarus_coordinator.storage.LockUserSessionRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class LockUserSessionService {
    @Autowired
    lateinit var lockUserSessionRepository: LockUserSessionRepository
    @Autowired
    lateinit var timeSource: TimeSource

    fun saveNewLockUserSession(lockSession: LockSession, userPublicKey : String) : LockUserSession {
        val maybeLockUserSession = lockUserSessionRepository.findByPublicKey(userPublicKey) ?: LockUserSession(
            publicKey = userPublicKey,
            lockSession = lockSession,
            createdAt = timeSource.nowInUtc(),
            updatedAt = timeSource.nowInUtc()
        ).let { lockUserSessionRepository.save(it) }
        return maybeLockUserSession
    }

    fun findByName(name : String) : LockUserSession {
        return lockUserSessionRepository.findByName(name)
    }
}