package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.api.messages.BotMessage
import club.subjugated.tartarus_coordinator.api.messages.NewBotMessage
import club.subjugated.tartarus_coordinator.services.BotService
import club.subjugated.tartarus_coordinator.services.EventService
import jakarta.ws.rs.core.MediaType
import java.util.Base64
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

data class BotEventReplayItem(
    val eventId: String,
    val contractSerial: Int,
    val counter: Int,
    val eventType: String,
    val correlationId: String?,
    val rawSignedEventBase64: String,
    val createdAt: String,
)

data class BotEventsReplayResponse(
    val botName: String,
    val contractName: String,
    val latestCounter: Int,
    val events: List<BotEventReplayItem>,
)

@RestController
@RequestMapping("/bots")
@Controller
class BotController {
    @Autowired lateinit var botService: BotService
    @Autowired lateinit var eventService: EventService

    @GetMapping("/", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun getBots(): ResponseEntity<List<BotMessage>> {
        val bots = botService.getAll()
        return ResponseEntity.ok(bots.map { BotMessage.fromBot(it) })
    }

    @PostMapping("/", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun newBot(@RequestBody newBotMessage: NewBotMessage): ResponseEntity<BotMessage> {
        val bot = botService.createNewBot(newBotMessage)

        return ResponseEntity.ok(BotMessage.fromBot(bot))
    }

    @GetMapping("/{bot_name}/events", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun getEvents(
        @PathVariable("bot_name") botName: String,
        @RequestParam("contract_name") contractName: String,
        @RequestParam("since_counter", defaultValue = "0") sinceCounter: Int,
        @RequestParam("limit", defaultValue = "50") limit: Int,
    ): ResponseEntity<BotEventsReplayResponse> {
        val deliveries =
            eventService.getMissedEventsForBot(botName, contractName, sinceCounter).take(limit)
        val items =
            deliveries.map { d ->
                val rawB64 = d.event.body?.let { Base64.getEncoder().encodeToString(it) } ?: ""
                BotEventReplayItem(
                    eventId = d.event.name,
                    contractSerial = d.event.contractSerialNumber,
                    counter = d.event.counter,
                    eventType = d.event.eventType,
                    correlationId = d.event.correlationId,
                    rawSignedEventBase64 = rawB64,
                    createdAt = d.event.createdAt.toString(),
                )
            }
        val latestCounter = if (items.isNotEmpty()) items.last().counter else sinceCounter
        return ResponseEntity.ok(
            BotEventsReplayResponse(
                botName = botName,
                contractName = contractName,
                latestCounter = latestCounter,
                events = items,
            )
        )
    }
}
