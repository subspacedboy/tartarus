package club.subjugated.overlord_exe.bots.timer_bot

import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.convo.ConversationResponse
import club.subjugated.overlord_exe.convo.IntentSpec
import club.subjugated.overlord_exe.convo.unitIntent
import club.subjugated.overlord_exe.models.BotEngagement
import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.EngagementPhase
import club.subjugated.overlord_exe.models.TimerEngagementData
import club.subjugated.overlord_exe.runtime.BotContext
import club.subjugated.overlord_exe.runtime.BotDescriptor
import club.subjugated.overlord_exe.runtime.OverlordBot
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.UrlService
import club.subjugated.overlord_exe.storage.BotEngagementRepository
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.extractShareableToken
import club.subjugated.overlord_exe.util.formatDuration
import club.subjugated.overlord_exe.util.generateId
import jakarta.annotation.PostConstruct
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import org.ocpsoft.prettytime.PrettyTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.temporal.ChronoUnit
import kotlin.random.Random

data class TimeForm(
    var name: String,
    @field:Pattern(
        regexp = "^(?:s|tc)-[0-9a-zA-Z]{7}$",
        message = "Shareable token must start with S or TC, have a hyphen, and have 7 characters",
    )
    var shareableToken: String,
    var random: Boolean = false,
    var public: Boolean = false,
    @field:Positive
    var minDuration: Int? = 1,
    var minUnit: String = "HOURS",
    @field:Positive
    var maxDuration: Int? = 1,
    var maxUnit: String = "HOURS",
) {
    @get:AssertTrue(message = "Minimum duration must be less than maximum duration")
    val validDuration: Boolean
        get() {
            if (minDuration == null || maxDuration == null) return true
            return try {
                val min = ChronoUnit.valueOf(minUnit.uppercase()).duration.seconds * minDuration!!
                val max = ChronoUnit.valueOf(maxUnit.uppercase()).duration.seconds * maxDuration!!
                min <= max
            } catch (_: Exception) {
                true
            }
        }
}

@Service
class TimerBot(
    private val engagementRepository: BotEngagementRepository,
    private val timeSource: TimeSource,
    private val botMapService: BotMapService,
    private val botComponent: BotComponent,
    private val urlService: UrlService = UrlService(),
    private val logger: Logger = LoggerFactory.getLogger(TimerFixedLogName),
) : OverlordBot {

    companion object {
        private val TimerFixedLogName = TimerBot::class.java
    }

    override val descriptor = BotDescriptor(
        internalName = "timer",
        description = "TimerBot",
        reviewInterval = Duration.ofMinutes(1),
    )

    override val commands: Set<String> = setOf("timer")

    override val intents: List<IntentSpec<*>> = listOf(
        unitIntent(
            name = "timer",
            description = "The user is trying to self-lock for a period of time. Trigger immediately without asking follow-up questions.",
            examples = listOf(
                "I want to self lock.",
                "Lock me for a few hours.",
                "Lock me up for a few hours daddy.",
            ),
        ) { ctx ->
            ConversationResponse(text = startTimerIntakeUrl(ctx))
        }
    )

    val botContext: BotContext
        get() = botComponent.contextFor(this)

    @PostConstruct
    fun start() {
        logger.info("Starting TimerBot")
        botComponent.startBot(getBotMap(), this)
    }

    fun getBotMap(): BotMap {
        return botMapService.getOrCreateBotMap(descriptor.internalName, descriptor.description)
    }

    // --- BlueSky DM & Intent Handling ---

    override fun handleCommand(ctx: ConversationContext, command: String, args: List<String>): String {
        return startTimerIntakeUrl(ctx)
    }

    private fun startTimerIntakeUrl(ctx: ConversationContext): String {
        val did = ctx.bskyUser?.did ?: ctx.currentMessage.sender.did
        val engagement = createInitialPlaceholderSession(did, ctx.convoId)
        return urlService.generateUrl("timer/${engagement.name}")
    }

    // --- Session & Intake Lifecycle ---

    fun createInitialPlaceholderSession(did: String, convoId: String): BotEngagement {
        val engagement = BotEngagement(
            name = generateId("tbr-"),
            botId = descriptor.internalName,
            phase = EngagementPhase.CREATED,
            contractSerialNumber = 0,
            subjectDid = did,
            convoId = convoId,
            createdAt = timeSource.nowInUtc(),
            updatedAt = timeSource.nowInUtc(),
            data = TimerEngagementData(),
        )
        return save(engagement)
    }

    fun getSessionByName(name: String): BotEngagement {
        return engagementRepository.findByName(name)
            ?: throw IllegalStateException("Unknown TimerBot engagement: $name")
    }

    fun processContractForm(form: TimeForm): BotEngagement {
        val engagement = getSessionByName(form.name)
        if (engagement.phase == EngagementPhase.ISSUED) {
            logger.warn("Attempted reprocessing ${form.name}")
            return engagement
        }

        val data = engagement.data as TimerEngagementData
        val token = extractShareableToken(form.shareableToken)
        engagement.shareableToken = token
        data.isRandom = form.random
        data.isPublic = form.public

        if (form.random) {
            data.minDuration = form.minDuration!!
            data.minDurationUnit = form.minUnit
        }
        data.maxDuration = form.maxDuration!!
        data.maxDurationUnit = form.maxUnit

        val terms = if (data.isRandom) {
            "Duration revealed on accept"
        } else {
            formatDuration(data.maxDuration.toLong(), form.maxUnit)
        }

        val contract = botContext.issueContract(
            shareableToken = token,
            terms = "Timer bot: $terms",
            public = data.isPublic,
        )

        engagement.contractSerialNumber = contract.serialNumber
        engagement.contractId = contract.id
        engagement.phase = EngagementPhase.ISSUED
        save(engagement)

        botContext.sendDm(engagement.convoId, "Contract issued")
        return engagement
    }

    fun findByContractIds(contractIds: List<Long>): List<BotEngagement> {
        return engagementRepository.findByBotIdAndContractIdIn(descriptor.internalName, contractIds)
    }

    fun save(engagement: BotEngagement): BotEngagement {
        engagement.syncJsonBeforeSave()
        engagement.updatedAt = timeSource.nowInUtc()
        return engagementRepository.save(engagement)
    }

    // --- Coordinator Contract Lifecycle ---

    override fun reviewContracts(ctx: BotContext, contracts: List<Contract>) {
        if (contracts.isEmpty()) return

        val engagements = findByContractIds(contracts.map { it.id })
        for (engagement in engagements) {
            val contract = contracts.find { it.id == engagement.contractId } ?: continue
            val data = engagement.data as? TimerEngagementData ?: continue

            if (data.endsAt != null && data.endsAt!! < timeSource.nowInUtc()) {
                ctx.addMessage(contract, "Time is up.")
                ctx.release(contract)
            }
        }
    }

    override fun reviewContracts(contracts: List<Contract>) {
        reviewContracts(botContext, contracts)
    }

    override fun onAccept(ctx: BotContext, contract: Contract) {
        val engagement = engagementRepository.findByBotIdAndContractSerialNumber(
            descriptor.internalName,
            contract.serialNumber,
        ) ?: return
        val data = engagement.data as TimerEngagementData

        val acceptedAt = timeSource.nowInUtc()
        engagement.acceptedAt = acceptedAt
        engagement.phase = EngagementPhase.ACTIVE
        engagement.contractId = contract.id

        data.endsAt = if (data.isRandom) {
            val minDuration = Duration.of(data.minDuration.toLong(), ChronoUnit.valueOf(data.minDurationUnit))
            val maxDuration = Duration.of(data.maxDuration.toLong(), ChronoUnit.valueOf(data.maxDurationUnit))
            val randomMinutes = Random.nextLong(minDuration.toMinutes(), maxDuration.toMinutes() + 1)
            acceptedAt.plus(Duration.ofMinutes(randomMinutes))
        } else {
            val duration = Duration.of(data.maxDuration.toLong(), ChronoUnit.valueOf(data.maxDurationUnit))
            acceptedAt.plus(duration)
        }

        if (data.isRandom) {
            val relativeDuration = PrettyTime().format(data.endsAt)
            ctx.addMessage(contract, "Ends $relativeDuration")
        }

        save(engagement)
    }

    override fun handleAccept(contract: Contract) {
        onAccept(botContext, contract)
    }

    override fun onRelease(ctx: BotContext, contract: Contract) {
        val engagements = findByContractIds(listOf(contract.id))
        for (engagement in engagements) {
            val data = engagement.data as? TimerEngagementData
            data?.completed = true
            engagement.phase = EngagementPhase.COMPLETE
            save(engagement)
        }
    }

    override fun handleRelease(contract: Contract) {
        onRelease(botContext, contract)
    }
}
