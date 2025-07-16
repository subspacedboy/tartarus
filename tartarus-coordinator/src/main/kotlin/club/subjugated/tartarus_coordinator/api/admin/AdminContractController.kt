package club.subjugated.tartarus_coordinator.api.admin

import club.subjugated.tartarus_coordinator.api.messages.AdminSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.CommandMessage
import club.subjugated.tartarus_coordinator.api.messages.ConfirmAdminSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.ContractMessage
import club.subjugated.tartarus_coordinator.models.ContractState
import club.subjugated.tartarus_coordinator.services.CommandQueueService
import club.subjugated.tartarus_coordinator.services.ContractService
import club.subjugated.tartarus_coordinator.services.SafetyKeyService
import jakarta.ws.rs.core.MediaType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/contracts")
@Controller
class AdminContractController {
    @Autowired
    lateinit var contractService: ContractService

    @Autowired
    lateinit var commandQueueService: CommandQueueService

    @Autowired
    lateinit var safetyKeyService: SafetyKeyService

    @GetMapping("/", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    fun getLiveContracts(
        @AuthenticationPrincipal admin: UserDetails,
        @RequestParam("states", required = false) statesParam: String?
    ): ResponseEntity<List<ContractMessage>> {
        val states = statesParam
            ?.split(",")
            ?.mapNotNull { runCatching { ContractState.valueOf(it.trim().uppercase()) }.getOrNull() }
            ?: listOf(ContractState.CONFIRMED)

        val contracts = this.contractService.getByStateAdminOnly(states)

        return ResponseEntity.ok(contracts.map { ContractMessage.fromContract(it) })
    }

    @GetMapping("/{someToken}", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    fun getContractByName(
        @AuthenticationPrincipal admin: UserDetails,
        @PathVariable someToken: String
    ): ResponseEntity<ContractMessage> {
        val contract = this.contractService.getByNameAdminOnly(someToken)

        return ResponseEntity.ok(ContractMessage.fromContract(contract))
    }

    @GetMapping("/{someToken}/commands/", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    fun getCommandsForName(
        @AuthenticationPrincipal admin: UserDetails,
        @PathVariable someToken: String
    ): ResponseEntity<List<CommandMessage>> {
        val contract = this.contractService.getByNameAdminOnly(someToken)
        val commands = this.commandQueueService.getByContract(contract)

        return ResponseEntity.ok(commands.map { CommandMessage.fromCommand(it) })
    }

    @PostMapping("/{someToken}/abort")
    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    fun abortContract(@AuthenticationPrincipal admin: UserDetails, @PathVariable someToken: String) : ResponseEntity<ContractMessage> {
        var contract = this.contractService.getByNameAdminOnly(someToken)
        val safetyKey = this.safetyKeyService.getAllActiveSafetyKeys().first()

        contract = this.contractService.abortContractWithSafetyKey(contract, safetyKey)

        return ResponseEntity.ok(ContractMessage.fromContract(contract))
    }

    @PostMapping("/{someToken}/reset")
    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    fun resetLock(@AuthenticationPrincipal admin: UserDetails, @PathVariable someToken: String) : ResponseEntity<ContractMessage> {
        var contract = this.contractService.getByNameAdminOnly(someToken)
        val safetyKey = this.safetyKeyService.getAllActiveSafetyKeys().first()

        contract = this.contractService.resetWithSafetyKey(contract, safetyKey)

        return ResponseEntity.ok(ContractMessage.fromContract(contract))
    }
}