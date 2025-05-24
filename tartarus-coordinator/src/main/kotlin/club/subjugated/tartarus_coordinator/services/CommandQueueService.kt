package club.subjugated.tartarus_coordinator.services

import club.subjugated.fb.message.Acknowledgement
import club.subjugated.tartarus_coordinator.events.AcknowledgedCommandEvent
import club.subjugated.tartarus_coordinator.filters.WebUserAuthenticationFilter
import club.subjugated.tartarus_coordinator.models.AuthorSession
import club.subjugated.tartarus_coordinator.models.Command
import club.subjugated.tartarus_coordinator.models.CommandState
import club.subjugated.tartarus_coordinator.models.CommandType
import club.subjugated.tartarus_coordinator.models.Contract
import club.subjugated.tartarus_coordinator.models.LockSession
import club.subjugated.tartarus_coordinator.storage.CommandQueueRepository
import club.subjugated.tartarus_coordinator.storage.CommandRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class CommandQueueService {
    @Autowired lateinit var commandQueueRepository: CommandQueueRepository
    @Autowired lateinit var commandRepository: CommandRepository
    @Autowired lateinit var timeSource: TimeSource
    @Autowired lateinit var publisher: ApplicationEventPublisher

    private val logger: Logger = LoggerFactory.getLogger(CommandQueueService::class.java)

    fun saveCommand(command: Command) {
        this.commandRepository.save(command)
    }

    fun saveCommandIgnoreDupes(command: Command) : Boolean {
        val queue = command.commandQueue
        val pendingCommands = queue.commands.sortedBy { it.createdAt }
        val lastPendingCommand = pendingCommands.lastOrNull()
        if(lastPendingCommand != null && command.type == lastPendingCommand.type) {
            logger.info("Ignoring duplicate command: $command")
            return false
        }
        this.commandRepository.save(command)
        return true
    }

    fun acknowledgeCommand(command: Command, ack: Acknowledgement) {
        command.state = CommandState.ACKNOWLEDGED

        if(command.type == CommandType.RELEASE) {
            command.commandQueue.lockSession.availableForContract = true
        }
        if(command.type == CommandType.ACCEPT_CONTRACT) {
            command.commandQueue.lockSession.availableForContract = false
        }

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

    fun getCommandBySessionAndSerial(lockSession: LockSession, serialNumber: Int): List<Command> {
        val commandQueueId = lockSession.commandQueue.first().id
        return this.commandRepository.findByCommandQueueIdAndSerialNumber(
            commandQueueId,
            serialNumber,
        )
    }

    fun getByAuthorSessionIdAndContract(authorSession: AuthorSession, contract : Contract) : List<Command> {
        return this.commandRepository.findByAuthorSessionIdAndContractIdOrderByCounterDesc(authorSession.id, contract.id)
    }

    fun getByContract(contract : Contract) : List<Command> {
        return this.commandRepository.findByContractIdOrderByCounterDesc(contract.id)
    }
}
