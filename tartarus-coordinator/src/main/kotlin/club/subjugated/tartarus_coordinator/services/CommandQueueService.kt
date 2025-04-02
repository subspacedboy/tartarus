package club.subjugated.tartarus_coordinator.services

import club.subjugated.fb.message.Acknowledgement
import club.subjugated.tartarus_coordinator.events.AcknowledgedCommandEvent
import club.subjugated.tartarus_coordinator.models.Command
import club.subjugated.tartarus_coordinator.models.CommandState
import club.subjugated.tartarus_coordinator.models.LockSession
import club.subjugated.tartarus_coordinator.storage.CommandQueueRepository
import club.subjugated.tartarus_coordinator.storage.CommandRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class CommandQueueService {
    @Autowired lateinit var commandQueueRepository: CommandQueueRepository
    @Autowired lateinit var commandRepository: CommandRepository
    @Autowired lateinit var timeSource: TimeSource
    @Autowired lateinit var publisher: ApplicationEventPublisher

    fun saveCommand(command: Command) {
        this.commandRepository.save(command)
    }

    fun acknowledgeCommand(command: Command, ack: Acknowledgement) {
        command.state = CommandState.ACKNOWLEDGED
        publisher.publishEvent(AcknowledgedCommandEvent(this, command, ack))
        saveCommand(command)
    }

    fun errorCommand(command: Command, message: String?) {
        command.state = CommandState.ERROR
        command.message = message
        saveCommand(command)
    }

    fun getPendingCommandsForSession(lockSession: LockSession): List<Command> {
        val commandQueueId = lockSession.commandQueue.first().id
        return this.commandRepository.findByCommandQueueIdAndStateOrderByCreatedAt(
            commandQueueId,
            CommandState.PENDING,
        )
    }

    fun getCommandBySessionAndSerial(lockSession: LockSession, serialNumber: Int): Command {
        val commandQueueId = lockSession.commandQueue.first().id
        return this.commandRepository.findByCommandQueueIdAndSerialNumber(
            commandQueueId,
            serialNumber,
        )
    }
}
