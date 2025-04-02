package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.api.messages.ContractMessage
import club.subjugated.tartarus_coordinator.api.messages.NewContractMessage
import club.subjugated.tartarus_coordinator.api.messages.NewUnlockCommandMessage
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.ContractService
import club.subjugated.tartarus_coordinator.services.LockSessionService
import jakarta.ws.rs.core.MediaType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/contracts")
@Controller
class ContractController {
    @Autowired
    lateinit var contractService: ContractService
    @Autowired
    lateinit var authorSessionService: AuthorSessionService
    @Autowired
    lateinit var lockSessionService: LockSessionService

    @GetMapping("/byShareable/{someToken}", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun getContractsByShareableToken(@PathVariable someToken : String) : ResponseEntity<List<ContractMessage>>{
        val contracts = this.contractService.findContractsByShareableToken(someToken)

        return ResponseEntity.ok(contracts.map { ContractMessage.fromContract(it) })
    }

    @GetMapping("/{name}", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun getContractsByName(@PathVariable name : String) : ResponseEntity<ContractMessage> {
        val contract = this.contractService.getByName(name)
        return ResponseEntity.ok(ContractMessage.fromContract(contract))
    }

    @PostMapping("/", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun saveContract(@RequestBody newContractMessage: NewContractMessage) : ResponseEntity<ContractMessage> {
        val lockSession = this.lockSessionService.findByShareableToken(newContractMessage.shareableToken!!)
        val authorSession = this.authorSessionService.findByName(newContractMessage.authorName!!)

        val contract = this.contractService.saveContract(newContractMessage, lockSession!!, authorSession)

        return ResponseEntity.ok(ContractMessage.fromContract(contract))
    }

    @PostMapping("/command", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun command(@RequestBody newUnlockCommandMessage: NewUnlockCommandMessage) : ResponseEntity<Void> {
        val authorSession = this.authorSessionService.findByName(newUnlockCommandMessage.authorSessionName!!)
        val lockSession = this.lockSessionService.findByShareableToken(newUnlockCommandMessage.shareableToken!!)
        val contract = this.contractService.getByName(newUnlockCommandMessage.contractName!!)

        this.contractService.saveCommand(authorSession, lockSession!!, contract, newUnlockCommandMessage.signedMessage!!)
        return ResponseEntity.ok().build()
    }
}