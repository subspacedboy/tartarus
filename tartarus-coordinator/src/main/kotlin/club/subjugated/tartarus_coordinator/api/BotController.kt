package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.api.messages.BotMessage
import club.subjugated.tartarus_coordinator.api.messages.ContractMessage
import club.subjugated.tartarus_coordinator.api.messages.NewBotMessage
import club.subjugated.tartarus_coordinator.api.messages.NewContractMessage
import club.subjugated.tartarus_coordinator.api.messages.SyncBotsRequest
import club.subjugated.tartarus_coordinator.api.messages.UpdateBotMessage
import club.subjugated.tartarus_coordinator.models.AuthorSession
import club.subjugated.tartarus_coordinator.models.Bot
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.BotService
import club.subjugated.tartarus_coordinator.services.ContractService
import club.subjugated.tartarus_coordinator.services.EventService
import club.subjugated.tartarus_coordinator.services.LockSessionService
import jakarta.ws.rs.core.MediaType
import java.util.Base64
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

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

data class BotLockSessionResponse(
    val name: String,
    val publicKey: String,
    val availableForContract: Boolean,
)

data class BotCreateCommandRequest(
    val contractName: String? = null,
    val signedMessage: String? = null,
    val signedMessageBase64: String? = null,
    val shareableToken: String? = null,
    val lockSessionToken: String? = null,
    val contractSerialNumber: Int? = null,
)

data class BotCreateMessageRequest(val message: String, val signedMessageBase64: String? = null)

@Suppress("TooManyFunctions")
@RestController
@RequestMapping("/bots")
@Controller
class BotController {
    @Autowired lateinit var botService: BotService
    @Autowired lateinit var authorSessionService: AuthorSessionService
    @Autowired lateinit var contractService: ContractService
    @Autowired lateinit var lockSessionService: LockSessionService
    @Autowired lateinit var eventService: EventService

    private fun requireAuthorSession(user: UserDetails?): AuthorSession {
        if (user == null || !user.username.startsWith("as-")) {
            throw ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Author session authentication is required to manage bots",
            )
        }
        return authorSessionService.findByName(user.username)
    }

    private fun authorizeBotAccess(user: UserDetails?, bot: Bot) {
        if (user == null) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")
        }
        val isBotSelf = user.username == bot.name
        val isBotOwner = user.username.startsWith("as-") && bot.authorSession?.name == user.username
        if (!isBotSelf && !isBotOwner) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Not authorized to act on behalf of bot ${bot.name}",
            )
        }
    }

    @GetMapping("/", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun getBots(): ResponseEntity<List<BotMessage>> {
        val bots = botService.getAll()
        return ResponseEntity.ok(bots.map { BotMessage.fromBot(it) })
    }

    @GetMapping("/mine", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun getMyBots(@AuthenticationPrincipal user: UserDetails?): ResponseEntity<List<BotMessage>> {
        val authorSession = requireAuthorSession(user)
        val bots = botService.getBotsForAuthor(authorSession)
        return ResponseEntity.ok(bots.map { BotMessage.fromBot(it) })
    }

    @PostMapping("/", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun newBot(
        @AuthenticationPrincipal user: UserDetails?,
        @RequestBody newBotMessage: NewBotMessage,
    ): ResponseEntity<BotMessage> {
        val authorSession = requireAuthorSession(user)
        val bot = botService.createNewBot(authorSession, newBotMessage)
        return ResponseEntity.ok(BotMessage.fromBot(bot))
    }

    @PostMapping("/sync", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun syncBots(
        @AuthenticationPrincipal user: UserDetails?,
        @RequestBody request: SyncBotsRequest,
    ): ResponseEntity<List<BotMessage>> {
        val authorSession = requireAuthorSession(user)
        val bots = botService.syncBots(authorSession, request)
        return ResponseEntity.ok(bots.map { BotMessage.fromBot(it) })
    }

    @PutMapping("/{bot_name}", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun updateBot(
        @AuthenticationPrincipal user: UserDetails?,
        @PathVariable("bot_name") botName: String,
        @RequestBody updateBotMessage: UpdateBotMessage,
    ): ResponseEntity<BotMessage> {
        val authorSession = requireAuthorSession(user)
        val bot = botService.updateBot(authorSession, botName, updateBotMessage)
        return ResponseEntity.ok(BotMessage.fromBot(bot))
    }

    @GetMapping(
        "/{bot_name}/lock_sessions/{shareable_token}",
        produces = [MediaType.APPLICATION_JSON],
    )
    @ResponseBody
    fun getLockSessionForBot(
        @AuthenticationPrincipal user: UserDetails?,
        @PathVariable("bot_name") botName: String,
        @PathVariable("shareable_token") shareableToken: String,
    ): ResponseEntity<BotLockSessionResponse> {
        val bot = botService.getByName(botName)
        authorizeBotAccess(user, bot)

        val lockSession =
            lockSessionService.findByShareableToken(shareableToken)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Shareable token not found")

        return ResponseEntity.ok(
            BotLockSessionResponse(
                name = lockSession.name,
                publicKey = lockSession.publicKey,
                availableForContract = lockSession.availableForContract,
            )
        )
    }

    @PostMapping("/{bot_name}/contracts", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun createContractForBot(
        @AuthenticationPrincipal user: UserDetails?,
        @PathVariable("bot_name") botName: String,
        @RequestBody request: NewContractMessage,
    ): ResponseEntity<ContractMessage> {
        val bot = botService.getByName(botName)
        authorizeBotAccess(user, bot)

        val shareable = request.shareableToken ?: request.lockSessionToken ?: ""
        val lockSession =
            lockSessionService.findByShareableToken(shareable)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Shareable token not found")

        val ownerAuthorSession =
            bot.authorSession ?: authorSessionService.saveAuthorSessionForBot(bot.publicKey)

        val contractMessageWithAuthor =
            NewContractMessage(
                shareableToken = shareable,
                authorName = ownerAuthorSession.name,
                signedMessage = request.signedMessage ?: request.signedMessageBase64,
                notes = request.notes ?: "",
            )

        val contract =
            contractService.saveContract(contractMessageWithAuthor, lockSession, ownerAuthorSession)
        return ResponseEntity.ok(ContractMessage.fromContract(contract))
    }

    @GetMapping(
        "/{bot_name}/contracts/bySession/{lock_session_token}/{serial_number}",
        produces = [MediaType.APPLICATION_JSON],
    )
    @ResponseBody
    fun getContractBySessionAndSerialForBot(
        @AuthenticationPrincipal user: UserDetails?,
        @PathVariable("bot_name") botName: String,
        @PathVariable("lock_session_token") lockSessionToken: String,
        @PathVariable("serial_number") serialNumber: Int,
    ): ResponseEntity<ContractMessage> {
        val bot = botService.getByName(botName)
        authorizeBotAccess(user, bot)

        val lockSession =
            lockSessionService.findBySessionToken(lockSessionToken)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Lock session not found")

        val contract =
            contractService.getBySerialAndLockSessionForBot(lockSession, serialNumber, botName)
        return ResponseEntity.ok(ContractMessage.fromContract(contract))
    }

    @GetMapping("/{bot_name}/contracts/{contract_name}", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun getContractByNameForBot(
        @AuthenticationPrincipal user: UserDetails?,
        @PathVariable("bot_name") botName: String,
        @PathVariable("contract_name") contractName: String,
    ): ResponseEntity<ContractMessage> {
        val bot = botService.getByName(botName)
        authorizeBotAccess(user, bot)

        val contract = contractService.getByNameForBot(contractName, botName)
        return ResponseEntity.ok(ContractMessage.fromContract(contract))
    }

    @PostMapping("/{bot_name}/commands", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun createCommandForBot(
        @AuthenticationPrincipal user: UserDetails?,
        @PathVariable("bot_name") botName: String,
        @RequestBody request: BotCreateCommandRequest,
    ): ResponseEntity<Void> {
        val bot = botService.getByName(botName)
        authorizeBotAccess(user, bot)

        val contract =
            if (!request.contractName.isNullOrBlank()) {
                contractService.getByNameForBot(request.contractName, botName)
            } else {
                val token = request.lockSessionToken ?: request.shareableToken ?: ""
                val lockSession =
                    lockSessionService.findByShareableToken(token)
                        ?: lockSessionService.findBySessionToken(token)
                        ?: throw ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Lock session not found",
                        )
                contractService.getBySerialAndLockSessionForBot(
                    lockSession,
                    request.contractSerialNumber ?: 0,
                    botName,
                )
            }
        val payloadB64 = request.signedMessage ?: request.signedMessageBase64 ?: ""
        contractService.saveCommandForBot(bot, contract, payloadB64)
        return ResponseEntity.ok().build()
    }

    @PostMapping(
        "/{bot_name}/contracts/{contract_name}/messages",
        produces = [MediaType.APPLICATION_JSON],
    )
    @ResponseBody
    fun addMessageForBot(
        @AuthenticationPrincipal user: UserDetails?,
        @PathVariable("bot_name") botName: String,
        @PathVariable("contract_name") contractName: String,
        @RequestBody request: BotCreateMessageRequest,
    ): ResponseEntity<Void> {
        val bot = botService.getByName(botName)
        authorizeBotAccess(user, bot)

        val contract = contractService.getByNameForBot(contractName, botName)
        contractService.addMessageForBot(request.message, bot, contract)
        return ResponseEntity.ok().build()
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
