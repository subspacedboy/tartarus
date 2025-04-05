package club.subjugated.tartarus_coordinator.services

import club.subjugated.fb.message.AbortCommand
import club.subjugated.fb.message.ResetCommand
import club.subjugated.fb.message.SignedMessage
import club.subjugated.fb.message.firmware.MessagePayload
import club.subjugated.tartarus_coordinator.api.messages.NewContractMessage
import club.subjugated.tartarus_coordinator.events.AcknowledgedCommandEvent
import club.subjugated.tartarus_coordinator.events.ContractChangeEvent
import club.subjugated.tartarus_coordinator.events.NewCommandEvent
import club.subjugated.tartarus_coordinator.models.AuthorSession
import club.subjugated.tartarus_coordinator.models.Bot
import club.subjugated.tartarus_coordinator.models.Command
import club.subjugated.tartarus_coordinator.models.CommandState
import club.subjugated.tartarus_coordinator.models.CommandType
import club.subjugated.tartarus_coordinator.models.Contract
import club.subjugated.tartarus_coordinator.models.ContractState
import club.subjugated.tartarus_coordinator.models.LockSession
import club.subjugated.tartarus_coordinator.models.Message
import club.subjugated.tartarus_coordinator.models.MessageType
import club.subjugated.tartarus_coordinator.models.SafetyKey
import club.subjugated.tartarus_coordinator.storage.ContractRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import club.subjugated.tartarus_coordinator.util.ValidatedPayload
import club.subjugated.tartarus_coordinator.util.derToRawSignature
import club.subjugated.tartarus_coordinator.util.encodePublicKeySecp1
import club.subjugated.tartarus_coordinator.util.loadECPublicKeyFromPkcs8
import club.subjugated.tartarus_coordinator.util.signedMessageBytesValidator
import club.subjugated.tartarus_coordinator.util.signedMessageBytesValidatorWithExternalKey
import com.google.flatbuffers.FlatBufferBuilder
import org.jsoup.Connection.Base
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

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
                        lockSession = lockSession,
                        state = ContractState.CREATED,
                        body = Base64.getDecoder().decode(newContractMessage.signedMessage),
                        notes = newContractMessage.notes,
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

    fun approveContract(lockSession: LockSession, contractName: String) : Contract {
        val contract = contractRepository.findByName(contractName)
        assert(lockSession == contract.lockSession)

        contract.state = ContractState.ACCEPTED
        val command =
            Command(
                commandQueue = lockSession.commandQueue.first(),
                state = CommandState.PENDING,
                type = CommandType.ACCEPT_CONTRACT,
                serialNumber = contract.serialNumber,
                counter = 0,
                body = contract.body,
                authorSession = contract.authorSession,
                createdAt = timeSource.nowInUtc(),
                updatedAt = timeSource.nowInUtc(),
                contract = contract,
            )
        this.commandQueueService.saveCommand(command)

        publisher.publishEvent(NewCommandEvent(this, lockSession.sessionToken!!))

        return contract
    }

    fun rejectContract(lockSession: LockSession, contractName: String) : Contract {
        val contract = contractRepository.findByName(contractName)
        assert(lockSession == contract.lockSession)
        contract.state = ContractState.REJECTED
        contractRepository.save(contract)

        publisher.publishEvent(ContractChangeEvent(this, contract))
        return contract
    }

    fun findByLockSessionId(lockSession: LockSession) : List<Contract> {
        return contractRepository.findByLockSessionIdOrderByCreatedAtDesc(lockSession.id)
    }

    fun findByLockSessionIdAndState(lockSession: LockSession, state: List<ContractState>) : List<Contract> {
        return contractRepository.findByLockSessionIdAndStateInOrderByCreatedAtDesc(lockSession.id, state)
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
                assert(incomingCommand.unlockCommand.serialNumber.toInt() != 0)

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
                assert(incomingCommand.lockCommand.serialNumber.toInt() != 0)
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
                assert(incomingCommand.releaseCommand.serialNumber.toInt() != 0)
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

    fun findContractsByAuthorSessionAndState(authorSession: AuthorSession, contractState: ContractState) : List<Contract> {
        return contractRepository.findByAuthorSessionIdAndStateOrderByCreatedAtDesc(authorSession.id, contractState)
    }

    fun findContractsByAuthorSessionAndShareableToken(authorSession: AuthorSession, someToken: String): List<Contract> {
        return this.contractRepository.findByAuthorSessionIdAndShareableTokenOrderByCreatedAtDesc(authorSession.id, someToken)
    }

    fun getByNameForAuthor(name: String): Contract {
        val contract = this.contractRepository.findByName(name)
        if(contract.state == ContractState.CONFIRMED) {
            contract.lockState = contract.lockSession.isLocked
        }
        return contract
    }

    fun addMessageForBot(message: String, bot : Bot, contract: Contract) {
        val contractMessage = Message(
            body = message,
            type = MessageType.BOT_MESSAGE,
            contract = contract,
            bot = bot,
            createdAt = timeSource.nowInUtc(),
            updatedAt = timeSource.nowInUtc()
        )

        contract.messages.add(contractMessage)
        contractRepository.save(contract)
    }

    fun getBySerialAndLockSessionForBot(lockSession: LockSession, serial: Int, botName: String) : Contract {
        val contract = this.contractRepository.findByLockSessionIdAndSerialNumber(lockSession.id, serial)
        val embeddedBots = contract.getEmbeddedBots()
        val bot = embeddedBots.firstOrNull { it.name == botName }
        if(bot == null){
            throw IllegalArgumentException("Bot isn't listed in the contract")
        }

        return contract
    }

    fun getByStateAdminOnly(states : List<ContractState>) : List<Contract> {
        return contractRepository.findByStateIn(states)
    }

    fun getByNameAdminOnly(name : String) : Contract {
        return contractRepository.findByName(name)
    }

    fun abortContractWithSafetyKey(contract: Contract, safetyKey : SafetyKey) : Contract {
        val builder = FlatBufferBuilder(1024)

        AbortCommand.startAbortCommand(builder)
        AbortCommand.addContractSerialNumber(builder, contract.serialNumber.toUShort())
        val counter = contract.nextCounter + 1
        AbortCommand.addCounter(builder, counter.toUShort())
        val serialNumber = 45
        AbortCommand.addSerialNumber(builder, serialNumber.toUShort())
        val abortOffset = AbortCommand.endAbortCommand(builder)

        builder.finish(abortOffset)
        val data = builder.sizedByteArray()

        val offsetToTable = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
        val offsetToVTable = (data[offsetToTable].toInt() and 0xFF) or ((data[offsetToTable + 1].toInt() and 0xFF) shl 8)
        val vTableStart = offsetToTable - offsetToVTable
        val vtableAndContract = data.copyOfRange(vTableStart, data.size)

        val keySpec = PKCS8EncodedKeySpec(safetyKey.privateKey)
        val keyFactory = KeyFactory.getInstance("EC", "BC")
        val privateKey = keyFactory.generatePrivate(keySpec)

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(vtableAndContract)
        val hash = digest.digest()

        val signature = Signature.getInstance("SHA256withECDSA", "BC").apply {
            initSign(privateKey)
            update(hash)
        }.sign()

        val rawSig = derToRawSignature(signature)

        val authorityOffset = builder.createString(safetyKey.name)
        val sigOffset = builder.createByteVector(rawSig)

        SignedMessage.startSignedMessage(builder)
        SignedMessage.addPayloadType(builder, club.subjugated.fb.message.MessagePayload.AbortCommand)
        SignedMessage.addPayload(builder, abortOffset)
        SignedMessage.addSignature(builder, sigOffset)
        SignedMessage.addAuthorityIdentifier(builder, authorityOffset)
        val smOffset = SignedMessage.endSignedMessage(builder)

        builder.finish(smOffset)
        val signedMessage = builder.sizedByteArray()

        val publicKey = loadECPublicKeyFromPkcs8(safetyKey.publicKey!!)
        val ecPublicKey = publicKey as java.security.interfaces.ECPublicKey
        val compressedPubKey = encodePublicKeySecp1(ecPublicKey)

        val validated = signedMessageBytesValidatorWithExternalKey(ByteBuffer.wrap(signedMessage), compressedPubKey)

        val command =
            Command(
                commandQueue = contract.lockSession.commandQueue.first(),
                state = CommandState.PENDING,
                type = CommandType.ABORT,
                serialNumber = serialNumber,
                counter = counter,
                body = signedMessage,
                authorSession = contract.authorSession,
                createdAt = timeSource.nowInUtc(),
                updatedAt = timeSource.nowInUtc(),
                contract = contract,
            )
        this.commandQueueService.saveCommand(command)
        publisher.publishEvent(NewCommandEvent(this, contract.lockSession.sessionToken!!))

        contract.state = ContractState.ABORTED
        contractRepository.save(contract)

        return contract
    }

    // It's not a contract operation per se, but it's processed, handled, and structured like all the other
    // "contract" commands. There's almost certainly an associated contract though to wind up in this situation.
    fun resetWithSafetyKey(contract: Contract, safetyKey : SafetyKey) : Contract {
        val builder = FlatBufferBuilder(1024)

        val sessionTokenOffset = builder.createString(contract.lockSession.sessionToken!!)

        ResetCommand.startResetCommand(builder)
        ResetCommand.addSession(builder, sessionTokenOffset)
        val serialNumber = 55
        AbortCommand.addSerialNumber(builder, serialNumber.toUShort())
        val resetOffset = ResetCommand.endResetCommand(builder)

        builder.finish(resetOffset)
        val data = builder.sizedByteArray()

        val offsetToTable = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
        val offsetToVTable = (data[offsetToTable].toInt() and 0xFF) or ((data[offsetToTable + 1].toInt() and 0xFF) shl 8)
        val vTableStart = offsetToTable - offsetToVTable
        val vtableAndContract = data.copyOfRange(vTableStart, data.size)

        val keySpec = PKCS8EncodedKeySpec(safetyKey.privateKey)
        val keyFactory = KeyFactory.getInstance("EC", "BC")
        val privateKey = keyFactory.generatePrivate(keySpec)

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(vtableAndContract)
        val hash = digest.digest()

        val signature = Signature.getInstance("SHA256withECDSA", "BC").apply {
            initSign(privateKey)
            update(hash)
        }.sign()

        val rawSig = derToRawSignature(signature)

        val authorityOffset = builder.createString(safetyKey.name)
        val sigOffset = builder.createByteVector(rawSig)

        SignedMessage.startSignedMessage(builder)
        SignedMessage.addPayloadType(builder, club.subjugated.fb.message.MessagePayload.ResetCommand)
        SignedMessage.addPayload(builder, resetOffset)
        SignedMessage.addSignature(builder, sigOffset)
        SignedMessage.addAuthorityIdentifier(builder, authorityOffset)
        val smOffset = SignedMessage.endSignedMessage(builder)

        builder.finish(smOffset)
        val signedMessage = builder.sizedByteArray()

        val publicKey = loadECPublicKeyFromPkcs8(safetyKey.publicKey!!)
        val ecPublicKey = publicKey as java.security.interfaces.ECPublicKey
        val compressedPubKey = encodePublicKeySecp1(ecPublicKey)

        val validated = signedMessageBytesValidatorWithExternalKey(ByteBuffer.wrap(signedMessage), compressedPubKey)

        val command =
            Command(
                commandQueue = contract.lockSession.commandQueue.first(),
                state = CommandState.PENDING,
                type = CommandType.RESET,
                serialNumber = serialNumber,
                counter = 0,
                body = signedMessage,
                authorSession = contract.authorSession,
                createdAt = timeSource.nowInUtc(),
                updatedAt = timeSource.nowInUtc(),
                contract = contract,
            )
        this.commandQueueService.saveCommand(command)
        publisher.publishEvent(NewCommandEvent(this, contract.lockSession.sessionToken!!))

        contract.state = ContractState.ABORTED
        contractRepository.save(contract)

        return contract
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
