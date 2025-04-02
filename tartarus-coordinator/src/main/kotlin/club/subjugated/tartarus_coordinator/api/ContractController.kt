package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.api.messages.ContractMessage
import club.subjugated.tartarus_coordinator.api.messages.NewContractMessage
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.ContractService
import club.subjugated.tartarus_coordinator.util.signedMessageBytesValidator
import jakarta.ws.rs.core.MediaType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import java.nio.ByteBuffer
import java.util.*

@RestController
@RequestMapping("/contracts")
@Controller
class ContractController {
    @Autowired
    lateinit var contractService: ContractService
    @Autowired
    lateinit var authorSessionService: AuthorSessionService

    @PostMapping("/", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun saveContract(@RequestBody newContractMessage: NewContractMessage) : ResponseEntity<ContractMessage> {
        val response = ContractMessage(id = "moo", name="cow")

        val authorSession = this.authorSessionService.findByName(newContractMessage.authorName!!)

        this.contractService.saveContract(newContractMessage, authorSession)

        return ResponseEntity.ok(response)
    }
}