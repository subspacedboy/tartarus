package club.subjugated.tartarus_coordinator.services

import club.subjugated.fb.message.PeriodicUpdate
import club.subjugated.tartarus_coordinator.api.messages.NewLockSessionMessage
import club.subjugated.tartarus_coordinator.events.PeriodicUpdateEvent
import club.subjugated.tartarus_coordinator.models.*
import club.subjugated.tartarus_coordinator.storage.CommandQueueRepository
import club.subjugated.tartarus_coordinator.storage.LockSessionRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import club.subjugated.tartarus_coordinator.util.generateId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class LockSessionService {
    @Autowired lateinit var timeSource: TimeSource
    @Autowired lateinit var lockSessionRepository: LockSessionRepository
    @Autowired lateinit var commandQueueRepository: CommandQueueRepository
    @Autowired lateinit var contractService: ContractService
    @Autowired lateinit var publisher: ApplicationEventPublisher

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

    fun handlePeriodicUpdate(periodicUpdate: PeriodicUpdate) {
        val lockSession = findBySessionToken(periodicUpdate.session!!)

        lockSession.isLocked = periodicUpdate.isLocked
        saveLockSession(lockSession)
        val internalUpdate = if(periodicUpdate.currentContractSerial.toInt() != 0){
            val contracts = contractService.findByLockSessionIdAndState(lockSession, listOf(ContractState.CONFIRMED))
            val activeContract = contracts.firstOrNull { it.serialNumber == periodicUpdate.currentContractSerial.toInt() }
            PeriodicUpdateEvent(this, lockSession, activeContract)
        } else {
            PeriodicUpdateEvent(this, lockSession, null)
        }

        publisher.publishEvent(internalUpdate)
    }

    fun findByShareableToken(someToken: String): LockSession? {
        val maybeSession =
            this.lockSessionRepository.findByShareTokenOrTotalControlToken(someToken, someToken)
        return maybeSession
    }

    fun saveLockSession(lockSession: LockSession) {
        lockSession.updatedAt = timeSource.nowInUtc()
        this.lockSessionRepository.save(lockSession)
    }
}
