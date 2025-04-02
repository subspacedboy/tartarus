package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.api.messages.BotMessage
import club.subjugated.tartarus_coordinator.api.messages.NewBotMessage
import club.subjugated.tartarus_coordinator.api.messages.NewCommandMessage
import club.subjugated.tartarus_coordinator.services.BotService
import jakarta.ws.rs.core.MediaType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/bots")
@Controller
class BotController {
    @Autowired
    lateinit var botService: BotService

    @GetMapping("/", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun getBots() : ResponseEntity<List<BotMessage>> {
        val bots = botService.getAll()
        return ResponseEntity.ok(bots.map { BotMessage.fromBot(it) })
    }

    @PostMapping("/", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun newBot(
        @RequestBody newBotMessage: NewBotMessage
    ): ResponseEntity<BotMessage> {
        val bot = botService.createNewBot(newBotMessage)

        return ResponseEntity.ok(BotMessage.fromBot(bot))
    }
}