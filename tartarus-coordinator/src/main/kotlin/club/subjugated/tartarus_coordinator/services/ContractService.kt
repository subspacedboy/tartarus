package club.subjugated.tartarus_coordinator.services

import club.subjugated.tartarus_coordinator.api.messages.NewContractMessage
import club.subjugated.tartarus_coordinator.events.AcknowledgedCommandEvent
import club.subjugated.tartarus_coordinator.models.*
import club.subjugated.tartarus_coordinator.storage.ContractRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import club.subjugated.tartarus_coordinator.util.ValidatedPayload
import club.subjugated.tartarus_coordinator.util.signedMessageBytesValidator
import club.subjugated.tartarus_coordinator.util.signedMessageBytesValidatorWithExternalKey
import java.nio.ByteBuffer
import java.util.Base64
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class ContractService {
    @Autowired lateinit var contractRepository: ContractRepository
    @Autowired lateinit var commandQueueService: CommandQueueService
    @Autowired lateinit var timeSource: TimeSource
    @Autowired lateinit var publisher: ApplicationEventPublisher

    fun saveContract(
        newContractMessage: NewContractMessage,
        lockSession: LockSession,
        authorSession: AuthorSession,
    ): Contract {
        val decodedData = Base64.getDecoder().decode(newContractMessage.signedMessage!!)
        val buf = ByteBuffer.wrap(decodedData)

        val maybeContract = signedMessageBytesValidator(buf)
        when (maybeContract) {
            is ValidatedPayload.ContractPayload -> {
                val contract =
                    Contract(
                        shareableToken = newContractMessage.shareableToken,
                        state = ContractState.CREATED,
                        body = Base64.getDecoder().decode(newContractMessage.signedMessage),
                        authorSession = authorSession,
                        nextCounter = 0,
                        serialNumber = maybeContract.contract.serialNumber.toInt(),
                        createdAt = timeSource.nowInUtc(),
                        updatedAt = timeSource.nowInUtc(),
                    )

                this.contractRepository.save(contract)

                if (newContractMessage.shareableToken == lockSession.totalControlToken) {
                    contract.state = ContractState.ACCEPTED
                    this.contractRepository.save(contract)

                    val command =
                        Command(
                            commandQueue = lockSession.commandQueue.first(),
                            state = CommandState.PENDING,
                            type = CommandType.ACCEPT_CONTRACT,
                            serialNumber = maybeContract.contract.serialNumber.toInt(),
                            counter = 0,
                            body = Base64.getDecoder().decode(newContractMessage.signedMessage),
                            authorSession = authorSession,
                            createdAt = timeSource.nowInUtc(),
                            updatedAt = timeSource.nowInUtc(),
                            contract = contract,
                        )
                    this.commandQueueService.saveCommand(command)

                    publisher.publishEvent(NewCommandEvent(this, lockSession.sessionToken!!))
                }

                return contract
            }
            else -> {
                throw IllegalArgumentException("Invalid signature")
            }
        }
    }

    fun saveCommand(
        authorSession: AuthorSession,
        lockSession: LockSession,
        contract: Contract,
        signedMessage: String,
    ) {
        val decodedData = Base64.getDecoder().decode(signedMessage)
        val authorPubKey = Base64.getDecoder().decode(authorSession.publicKey)
        val buf = ByteBuffer.wrap(decodedData)

        when (val incomingCommand = signedMessageBytesValidatorWithExternalKey(buf, authorPubKey)) {
            is ValidatedPayload.UnlockCommandPayload -> {
                val command =
                    Command(
                        commandQueue = lockSession.commandQueue.first(),
                        state = CommandState.PENDING,
                        type = CommandType.UNLOCK,
                        serialNumber = incomingCommand.unlockCommand.serialNumber.toInt(),
                        counter = incomingCommand.unlockCommand.counter.toInt(),
                        body = decodedData,
                        authorSession = authorSession,
                        createdAt = timeSource.nowInUtc(),
                        updatedAt = timeSource.nowInUtc(),
                        contract = contract,
                    )
                this.commandQueueService.saveCommand(command)
                publisher.publishEvent(NewCommandEvent(this, lockSession.sessionToken!!))
            }
            is ValidatedPayload.LockCommandPayload -> {
                val command =
                    Command(
                        commandQueue = lockSession.commandQueue.first(),
                        state = CommandState.PENDING,
                        type = CommandType.LOCK,
                        serialNumber = incomingCommand.lockCommand.serialNumber.toInt(),
                        counter = incomingCommand.lockCommand.counter.toInt(),
                        body = decodedData,
                        authorSession = authorSession,
                        createdAt = timeSource.nowInUtc(),
                        updatedAt = timeSource.nowInUtc(),
                        contract = contract,
                    )
                this.commandQueueService.saveCommand(command)
                publisher.publishEvent(NewCommandEvent(this, lockSession.sessionToken!!))
            }
            is ValidatedPayload.ReleaseCommandPayload -> {
                val command =
                    Command(
                        commandQueue = lockSession.commandQueue.first(),
                        state = CommandState.PENDING,
                        type = CommandType.RELEASE,
                        serialNumber = incomingCommand.releaseCommand.serialNumber.toInt(),
                        counter = incomingCommand.releaseCommand.counter.toInt(),
                        body = decodedData,
                        authorSession = authorSession,
                        createdAt = timeSource.nowInUtc(),
                        updatedAt = timeSource.nowInUtc(),
                        contract = contract,
                    )
                this.commandQueueService.saveCommand(command)
                publisher.publishEvent(NewCommandEvent(this, lockSession.sessionToken!!))
            }
            else -> {}
        }
    }

    fun findContractsByShareableToken(someToken: String): List<Contract> {
        return this.contractRepository.findByShareableTokenOrderByCreatedAtDesc(someToken)
    }

    fun getByName(name: String): Contract {
        return this.contractRepository.findByName(name)
    }

    @EventListener
    fun handleMessageEvent(event: AcknowledgedCommandEvent) {
        val command = event.command
        if (command.contract.state == ContractState.ACCEPTED && command.counter == 0) {
            val contract = command.contract
            contract.state = ContractState.CONFIRMED
            contract.nextCounter = 1
            this.contractRepository.save(contract)
        } else if (command.contract.nextCounter!! > 0) {
            val contract = command.contract
            contract.nextCounter = event.acknowledgement.counter.toInt() + 1
            this.contractRepository.save(contract)
        }

        if (
            command.contract.state == ContractState.CONFIRMED && command.type == CommandType.RELEASE
        ) {
            val contract = command.contract
            contract.state = ContractState.RELEASED
            this.contractRepository.save(contract)
        }
    }
}
