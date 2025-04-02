package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.api.messages.ContractMessage
import club.subjugated.tartarus_coordinator.api.messages.NewCommandMessage
import club.subjugated.tartarus_coordinator.api.messages.NewContractMessage
import club.subjugated.tartarus_coordinator.models.ContractState
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.ContractService
import club.subjugated.tartarus_coordinator.services.LockSessionService
import club.subjugated.tartarus_coordinator.services.LockUserSessionService
import jakarta.ws.rs.core.MediaType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/contracts")
@Controller
class ContractController {
    @Autowired lateinit var contractService: ContractService
    @Autowired lateinit var authorSessionService: AuthorSessionService
    @Autowired lateinit var lockSessionService: LockSessionService
    @Autowired lateinit var lockUserSessionService: LockUserSessionService

    @GetMapping("/byShareable/{someToken}", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun getContractsByShareableToken(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable someToken: String
    ): ResponseEntity<List<ContractMessage>> {
        val authorSession = this.authorSessionService.findByName(user.username)
        val contracts = this.contractService.findContractsByAuthorSessionAndShareableToken(authorSession, someToken)

        return ResponseEntity.ok(contracts.map { ContractMessage.fromContract(it) })
    }

    @GetMapping("/confirmed", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun getAuthorActiveContracts(
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<List<ContractMessage>> {
        val authorSession = this.authorSessionService.findByName(user.username)
        val contracts = this.contractService.findContractsByAuthorSessionAndState(authorSession, ContractState.CONFIRMED)

        return ResponseEntity.ok(contracts.map { ContractMessage.fromContract(it) })
    }

    @GetMapping("/{name}", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun getContractsByName(
        @AuthenticationPrincipal author: UserDetails,
        @PathVariable name: String): ResponseEntity<ContractMessage> {
        val contract = this.contractService.getByNameForAuthor(name)
        return ResponseEntity.ok(ContractMessage.fromContract(contract))
    }

    @GetMapping("/reviewPending", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun getForReview(
        @AuthenticationPrincipal lockUser: UserDetails,
    ): ResponseEntity<List<ContractMessage>> {
        val lockUserSession = lockUserSessionService.findByName(lockUser.username)
        val contracts = this.contractService.findByLockSessionIdAndState(lockUserSession.lockSession, listOf(ContractState.CREATED))
        return ResponseEntity.ok(contracts.map { ContractMessage.fromContract(it) })
    }

    @GetMapping("/byLockSession", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun getContractsForLockSession(
        @AuthenticationPrincipal lockUser: UserDetails,
    ): ResponseEntity<List<ContractMessage>> {
        val lockUserSession = lockUserSessionService.findByName(lockUser.username)
        val contracts = this.contractService.findByLockSessionId(lockUserSession.lockSession)
        return ResponseEntity.ok(contracts.map { ContractMessage.fromContract(it, skipMessages = true) })
    }

    @PostMapping("/approve/{contractName}", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun approve(@AuthenticationPrincipal lockUser: UserDetails, @PathVariable contractName : String): ResponseEntity<ContractMessage> {
        val lockUserSession = lockUserSessionService.findByName(lockUser.username)
        val contract = this.contractService.approveContract(lockUserSession.lockSession, contractName)
        return ResponseEntity.ok(ContractMessage.fromContract(contract))
    }

    @PostMapping("/reject/{contractName}", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun reject(@AuthenticationPrincipal lockUser: UserDetails, @PathVariable contractName : String): ResponseEntity<ContractMessage> {
        val lockUserSession = lockUserSessionService.findByName(lockUser.username)
        val contract = this.contractService.rejectContract(lockUserSession.lockSession, contractName)
        return ResponseEntity.ok(ContractMessage.fromContract(contract))
    }

    @PostMapping("/", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun saveContract(
        @AuthenticationPrincipal user: UserDetails,
        @RequestBody newContractMessage: NewContractMessage
    ): ResponseEntity<ContractMessage> {
        val lockSession =
            this.lockSessionService.findByShareableToken(newContractMessage.shareableToken!!)
        val authorSession = this.authorSessionService.findByName(newContractMessage.authorName!!)

        val contract =
            this.contractService.saveContract(newContractMessage, lockSession!!, authorSession)

        return ResponseEntity.ok(ContractMessage.fromContract(contract))
    }

    @PostMapping("/command", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun command(
        @AuthenticationPrincipal user: UserDetails,
        @RequestBody newCommandMessage: NewCommandMessage): ResponseEntity<Void> {
        val authorSession =
            this.authorSessionService.findByName(newCommandMessage.authorSessionName!!)
        val lockSession =
            this.lockSessionService.findByShareableToken(newCommandMessage.shareableToken!!)
        val contract = this.contractService.getByNameForAuthor(newCommandMessage.contractName!!)

        this.contractService.saveCommand(
            authorSession,
            lockSession!!,
            contract,
            newCommandMessage.signedMessage!!,
        )
        return ResponseEntity.ok().build()
    }
}
