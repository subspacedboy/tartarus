package club.subjugated.tartarus_coordinator.services

import club.subjugated.tartarus_coordinator.api.messages.NewLockSessionMessage
import club.subjugated.tartarus_coordinator.models.*
import club.subjugated.tartarus_coordinator.storage.CommandQueueRepository
import club.subjugated.tartarus_coordinator.storage.LockSessionRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import club.subjugated.tartarus_coordinator.util.generateId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class LockSessionService {
    @Autowired lateinit var timeSource: TimeSource
    @Autowired lateinit var lockSessionRepository: LockSessionRepository
    @Autowired lateinit var commandQueueRepository: CommandQueueRepository

    fun createLockSession(newLockSessionMessage: NewLockSessionMessage): LockSession {
        return lockSessionRepository.findBySessionToken(newLockSessionMessage.sessionToken)
            ?: run {
                val session =
                    LockSession(
                        publicKey = newLockSessionMessage.publicKey,
                        sessionToken = newLockSessionMessage.sessionToken,
                        shareToken = generateId("s-"),
                        totalControlToken = generateId("tc-"),
                        createdAt = timeSource.nowInUtc(),
                        updatedAt = timeSource.nowInUtc(),
                    )
                saveLockSession(session)

                // Also make the command queue, maybe move it to its own service if it gets
                // complicated
                val queue =
                    CommandQueue(
                        lockSession = session,
                        createdAt = timeSource.nowInUtc(),
                        updatedAt = timeSource.nowInUtc(),
                    )
                this.commandQueueRepository.save(queue)

                session
            }
    }

    fun findBySessionToken(token: String): LockSession {
        return this.lockSessionRepository.findBySessionToken(token)!!
    }

    fun findByShareableToken(someToken: String): LockSession? {
        val maybeSession =
            this.lockSessionRepository.findByShareTokenOrTotalControlToken(someToken, someToken)
        if (maybeSession != null) {
            // If the token we searched on was NOT the total control token, then for
            // safety just remove it.
            if (someToken == maybeSession.shareToken) {
                maybeSession.totalControlToken = null
            }
        }
        return maybeSession
    }

    fun saveLockSession(lockSession: LockSession) {
        lockSession.updatedAt = timeSource.nowInUtc()
        this.lockSessionRepository.save(lockSession)
    }
}
