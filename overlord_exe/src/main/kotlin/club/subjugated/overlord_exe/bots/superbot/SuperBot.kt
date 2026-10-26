package club.subjugated.overlord_exe.bots.superbot

import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.bots.superbot.convo.SuperbotIntent
import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.convo.ConversationResponse
import club.subjugated.overlord_exe.models.BotEngagement
import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.EngagementPhase
import club.subjugated.overlord_exe.models.ObjectiveStatus
import club.subjugated.overlord_exe.models.SuperBotEngagementData
import club.subjugated.overlord_exe.objectives.ObjectiveService
import club.subjugated.overlord_exe.runtime.BotContext
import club.subjugated.overlord_exe.runtime.BotDescriptor
import club.subjugated.overlord_exe.runtime.OverlordBot
import club.subjugated.overlord_exe.services.BSkyUserService
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.Intent
import club.subjugated.overlord_exe.services.UrlService
import club.subjugated.overlord_exe.storage.BotEngagementRepository
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.generateId
import jakarta.annotation.PostConstruct
import jakarta.validation.constraints.Size
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import java.time.Duration
import kotlin.reflect.KClass

data class IntakeForm(
    var name: String,
    var shareableToken: String,
    var public: Boolean = false,
    @field:Size(min = 1, message = "At least one objective must be selected")
    var objectives: List<String> = listOf("bsky_likes", "bsky_crowd_time"),
)

@Service
class SuperBot(
    private val engagementRepository: BotEngagementRepository,
    private val timeSource: TimeSource,
    private val botMapService: BotMapService,
    private val botComponent: BotComponent,
    private val objectiveService: ObjectiveService,
    private val bSkyUserService: BSkyUserService,
    private val urlService: UrlService = UrlService(),
    private val logger: Logger = LoggerFactory.getLogger(SuperBot::class.java),
) : OverlordBot {

    override val descriptor = BotDescriptor(
        internalName = "superBot",
        description = "OverLord SuperBot",
        reviewInterval = Duration.ofMinutes(1),
    )

    override val commands: Set<String> = setOf("superbot")

    val botContext: BotContext
        get() = botComponent.contextFor(this)

    @PostConstruct
    fun start() {
        logger.info("Starting SuperBot")
        botComponent.startBot(getBotMap(), this)
    }

    fun getBotMap(): BotMap {
        return botMapService.getOrCreateBotMap(descriptor.internalName, descriptor.description)
    }

    // --- BlueSky DM & Intent Handling ---

    override fun handle(convoId: String, message: ConvoDefsMessageView): String {
        val name = createPlaceholder(message.sender.did, convoId)
        return urlService.generateUrl("superbot/$name")
    }

    override fun getIntents(): List<KClass<out Intent>> = listOf(SuperbotIntent::class)

    override fun handleIntent(ctx: ConversationContext, intent: Intent): ConversationResponse {
        return when (intent) {
            is SuperbotIntent -> {
                val name = createPlaceholder(ctx.bskyUser!!.did, ctx.convoId)
                ConversationResponse(text = urlService.generateUrl("superbot/$name"))
            }
            else -> throw IllegalStateException("Unknown intent: $intent")
        }
    }

    // --- Session & Intake Lifecycle ---

    fun createPlaceholder(did: String, convoId: String): String {
        val user = bSkyUserService.findOrCreateByDid(did)
        val engagement = BotEngagement(
            name = generateId("sb-"),
            botId = descriptor.internalName,
            phase = EngagementPhase.CREATED,
            contractSerialNumber = 0,
            shareableToken = user.shareableToken ?: "",
            subjectDid = did,
            convoId = convoId,
            createdAt = timeSource.nowInUtc(),
            updatedAt = timeSource.nowInUtc(),
            data = SuperBotEngagementData(),
        )
        save(engagement)
        return engagement.name
    }

    fun processContractForm(form: IntakeForm) {
        val engagement = findByName(form.name)
        if (engagement.phase == EngagementPhase.ISSUED) {
            logger.warn("Attempted reprocessing ${form.name}")
            return
        }

        val data = engagement.data as SuperBotEngagementData
        data.isPublic = form.public
        data.objectives = form.objectives

        engagement.shareableToken = form.shareableToken
        engagement.phase = EngagementPhase.ISSUED

        val contract = botContext.issueContract(
            shareableToken = form.shareableToken,
            terms = "Superbot stuff",
            public = form.public,
        )

        engagement.contractSerialNumber = contract.serialNumber
        engagement.contractId = contract.id
        save(engagement)

        botContext.sendDm(
            convoId = engagement.convoId,
            message = "Contract issued",
        )
    }

    // --- Coordinator Contract Lifecycle ---

    override fun reviewContracts(ctx: BotContext, contracts: List<Contract>) {
        contracts.forEach { contract ->
            val engagement = engagementRepository.findByBotIdAndContractSerialNumber(
                descriptor.internalName,
                contract.serialNumber,
            ) ?: engagementRepository.findByContractId(contract.id) ?: return@forEach

            val state = objectiveService.process(engagement)
            if (state != null && state.status == ObjectiveStatus.COMPLETE) {
                ctx.release(contract)
            }
        }
    }

    override fun reviewContracts(contracts: List<Contract>) {
        reviewContracts(botContext, contracts)
    }

    override fun onAccept(ctx: BotContext, contract: Contract) {
        val engagement = findByContractSerialNumber(contract.serialNumber)
        engagement.acceptedAt = timeSource.nowInUtc()
        engagement.contractId = contract.id
        engagement.phase = EngagementPhase.ACTIVE
        save(engagement)

        val data = engagement.data as SuperBotEngagementData
        val chosenObjectiveId = data.objectives.random()
        objectiveService.startObjective(engagement, chosenObjectiveId)
    }

    override fun handleAccept(contract: Contract) {
        onAccept(botContext, contract)
    }

    override fun onRelease(ctx: BotContext, contract: Contract) {
        val engagement = findByContractSerialNumber(contract.serialNumber)
        engagement.phase = EngagementPhase.COMPLETE
        save(engagement)
    }

    override fun handleRelease(contract: Contract) {
        onRelease(botContext, contract)
    }

    fun findByName(name: String): BotEngagement {
        return engagementRepository.findByName(name)
            ?: throw IllegalStateException("Unknown SuperBot engagement: $name")
    }

    fun findByContractSerialNumber(serialNumber: Int): BotEngagement {
        return engagementRepository.findByBotIdAndContractSerialNumber(descriptor.internalName, serialNumber)
            ?: throw IllegalStateException("Unknown SuperBot engagement for serial $serialNumber")
    }

    fun save(engagement: BotEngagement): BotEngagement {
        engagement.syncJsonBeforeSave()
        engagement.updatedAt = timeSource.nowInUtc()
        return engagementRepository.save(engagement)
    }
}
