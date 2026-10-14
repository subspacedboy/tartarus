package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.api.messages.AcknowledgeCommandMessage
import club.subjugated.tartarus_coordinator.api.messages.CommandMessage
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.CommandQueueService
import club.subjugated.tartarus_coordinator.services.ContractService
import club.subjugated.tartarus_coordinator.services.LockSessionService
import club.subjugated.tartarus_coordinator.services.LockUserSessionService
import club.subjugated.tartarus_coordinator.util.ValidatedPayload
import club.subjugated.tartarus_coordinator.util.signedMessageBytesValidator
import jakarta.ws.rs.core.MediaType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.nio.ByteBuffer
import java.util.Base64

@RestController
@RequestMapping("/contracts/{contractName}/commands")
@Controller
class CommandController {
    @Autowired lateinit var contractService: ContractService
    @Autowired lateinit var authorSessionService: AuthorSessionService
    @Autowired lateinit var lockSessionService: LockSessionService
    @Autowired lateinit var lockUserSessionService: LockUserSessionService
    @Autowired lateinit var commandQueueService: CommandQueueService

    @GetMapping("/forAuthor", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun getCommandsForContractForAuthor(
        @AuthenticationPrincipal authorUser: UserDetails,
        @PathVariable contractName: String,
    ): ResponseEntity<List<CommandMessage>> {
        val authorUserSession = authorSessionService.findByName(authorUser.username)
        val contract = this.contractService.getByNameForAuthor(contractName)
        val commands =
            this.commandQueueService.getByAuthorSessionIdAndContract(authorUserSession, contract)

        return ResponseEntity.ok(commands.map { CommandMessage.fromCommand(it) })
    }

    @GetMapping("/forLockUser", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun getCommandsForContractForLockUser(
        @AuthenticationPrincipal lockUser: UserDetails,
        @PathVariable contractName: String,
    ): ResponseEntity<List<CommandMessage>> {
        val lockSessionUser = lockUserSessionService.findByName(lockUser.username)
        val contract = this.contractService.getByNameForAuthor(contractName)
        if (lockSessionUser.lockSession != contract.lockSession) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Lock session does not match contract",
            )
        }

        val commands = this.commandQueueService.getByContract(contract)

        return ResponseEntity.ok(commands.map { CommandMessage.fromCommand(it) })
    }

    @PostMapping(
        "/forLockUser/command/{commandName}/acknowledge",
        produces = [MediaType.APPLICATION_JSON],
    )
    @ResponseBody
    fun manuallyAcknowledgeCommand(
        @AuthenticationPrincipal lockUser: UserDetails,
        @PathVariable contractName: String,
        @PathVariable commandName: String,
        @RequestBody(required = false) body: AcknowledgeCommandMessage? = null,
    ): ResponseEntity<List<CommandMessage>> {
        val lockSessionUser = lockUserSessionService.findByName(lockUser.username)
        val contract = this.contractService.getByNameForAuthor(contractName)
        if (lockSessionUser.lockSession != contract.lockSession) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Lock session does not match contract",
            )
        }

        val commands = this.commandQueueService.getByContract(contract)

        val command =
            commands.firstOrNull { it.name == commandName }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Command not found")

        if (!body?.signedAcknowledgement.isNullOrBlank()) {
            val signedAckBase64 = body!!.signedAcknowledgement!!.trim()
            val ackBytes = try {
                Base64.getDecoder().decode(signedAckBase64)
            } catch (e: Exception) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Base64 payload", e)
            }

            val validatedPayload = try {
                signedMessageBytesValidator(ByteBuffer.wrap(ackBytes))
            } catch (e: Exception) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to parse signed message", e)
            }

            when (validatedPayload) {
                is ValidatedPayload.AcknowledgementPayload -> {
                    val ack = validatedPayload.acknowledgement
                    if (ack.session != contract.lockSession.sessionToken) {
                        throw ResponseStatusException(HttpStatus.FORBIDDEN, "Session token does not match lock")
                    }
                    val expectedSerial = command.serialNumber ?: contract.serialNumber
                    if (ack.serialNumber.toInt() != expectedSerial) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Serial number does not match command")
                    }
                    this.commandQueueService.acknowledgeCommand(command, ack)
                }
                is ValidatedPayload.ErrorPayload -> {
                    val err = validatedPayload.error
                    if (err.session != contract.lockSession.sessionToken) {
                        throw ResponseStatusException(HttpStatus.FORBIDDEN, "Session token does not match lock")
                    }
                    val expectedSerial = command.serialNumber ?: contract.serialNumber
                    if (err.serialNumber.toInt() != 0 && err.serialNumber.toInt() != expectedSerial) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Serial number does not match command")
                    }
                    this.commandQueueService.errorCommand(command, err.message)
                }
                else -> {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payload or signature verification failed")
                }
            }
        } else {
            this.commandQueueService.manuallyAcknowledgeCommand(command)
            contract.nextCounter = command.counter!! + 1
            this.contractService.save(contract)
        }

        val updatedCommands = this.commandQueueService.getByContract(contract)
        return ResponseEntity.ok(updatedCommands.map { CommandMessage.fromCommand(it) })
    }
}
