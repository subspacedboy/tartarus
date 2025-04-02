package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.api.messages.CommandMessage
import club.subjugated.tartarus_coordinator.api.messages.ContractMessage
import club.subjugated.tartarus_coordinator.models.ContractState
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.CommandQueueService
import club.subjugated.tartarus_coordinator.services.ContractService
import club.subjugated.tartarus_coordinator.services.LockSessionService
import club.subjugated.tartarus_coordinator.services.LockUserSessionService
import jakarta.ws.rs.core.MediaType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/contracts/{contractName}/commands")
@Controller
class CommandController {
    @Autowired
    lateinit var contractService: ContractService
    @Autowired
    lateinit var authorSessionService: AuthorSessionService
    @Autowired
    lateinit var lockSessionService: LockSessionService
    @Autowired
    lateinit var lockUserSessionService: LockUserSessionService
    @Autowired
    lateinit var commandQueueService: CommandQueueService

    @GetMapping("/forAuthor", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun getCommandsForContractForAuthor(
        @AuthenticationPrincipal authorUser: UserDetails,
        @PathVariable contractName: String
    ): ResponseEntity<List<CommandMessage>> {
        val authorUserSession = authorSessionService.findByName(authorUser.username)
        val contract = this.contractService.getByName(contractName)
        val commands = this.commandQueueService.getByAuthorSessionIdAndContract(authorUserSession, contract)

        return ResponseEntity.ok(commands.map { CommandMessage.fromCommand(it) })
    }

    @GetMapping("/forLockUser", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun getCommandsForContractForLockUser(
        @AuthenticationPrincipal lockUser: UserDetails,
        @PathVariable contractName: String
    ): ResponseEntity<List<CommandMessage>> {
        val lockSessionUser = lockUserSessionService.findByName(lockUser.username)
        val contract = this.contractService.getByName(contractName)
        assert(lockSessionUser.lockSession == contract.lockSession)

        val commands = this.commandQueueService.getByLockSessionIdAndContract(contract)

        return ResponseEntity.ok(commands.map { CommandMessage.fromCommand(it) })
    }


}