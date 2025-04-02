package club.subjugated.tartarus_coordinator.services

import club.subjugated.tartarus_coordinator.api.messages.NewLockSessionMessage
import club.subjugated.tartarus_coordinator.models.LockSession
import club.subjugated.tartarus_coordinator.storage.LockSessionRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import club.subjugated.tartarus_coordinator.util.generateId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class LockSessionService {
    @Autowired
    lateinit var timeSource: TimeSource
    @Autowired
    lateinit var lockSessionRepository: LockSessionRepository

    fun createLockSession(newLockSessionMessage: NewLockSessionMessage) : LockSession {

        return lockSessionRepository.findBySessionToken(newLockSessionMessage.sessionToken)
            ?: run {
                val session = LockSession(
                    publicKey = newLockSessionMessage.publicKey,
                    sessionToken = newLockSessionMessage.sessionToken,
                    shareToken = generateId("s-"),
                    totalControlToken = generateId("tc-"),
                    createdAt = timeSource.nowInUtc(),
                    updatedAt = timeSource.nowInUtc()
                )
                saveLockSession(session)
                session
            }
    }

    private fun saveLockSession(lockSession : LockSession) {
        this.lockSessionRepository.save(lockSession)
    }
}