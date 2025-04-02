package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.api.messages.ContractMessage
import club.subjugated.tartarus_coordinator.api.messages.NewContractMessage
import club.subjugated.tartarus_coordinator.util.signedMessageBytesValidator
import jakarta.ws.rs.core.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import java.nio.ByteBuffer
import java.util.*

@RestController
@RequestMapping("/contracts")
@Controller
class ContractController {

    @PostMapping("/", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun saveContract(@RequestBody newContractMessage: NewContractMessage) : ResponseEntity<ContractMessage> {
        val response = ContractMessage(id = "moo", name="cow")

        val decodedData = Base64.getDecoder().decode(newContractMessage.signedMessage!!)
        val buf = ByteBuffer.wrap(decodedData)

        val maybeContract = signedMessageBytesValidator(buf)

        return ResponseEntity.ok(response)
    }
}