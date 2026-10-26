package club.subjugated.overlord_exe.bots.timer_bot

import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.bots.timer_bot.web.TimeForm
import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.runtime.BotContext
import club.subjugated.overlord_exe.runtime.BotDescriptor
import club.subjugated.overlord_exe.runtime.OverlordBot
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.extractShareableToken
import club.subjugated.overlord_exe.util.formatDuration
import jakarta.annotation.PostConstruct
import org.ocpsoft.prettytime.PrettyTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.temporal.ChronoUnit
import kotlin.random.Random

@Service
class TimerBot(
    private val timerSessionRepository: TimerSessionRepository,
    private val timeSource: TimeSource,
    private val botMapService: BotMapService,
    private val botComponent: BotComponent,
    private val logger: Logger = LoggerFactory.getLogger(TimerBot::class.java),
) : OverlordBot {

    override val descriptor = BotDescriptor(
        internalName = "timer",
        description = "TimerBot",
        reviewInterval = Duration.ofMinutes(1),
    )

    val botContext: BotContext
        get() = botComponent.contextFor(this)

    @PostConstruct
    fun start() {
        logger.info("Starting TimerBot")
        val botMap = getBotMap()
        botComponent.startBot(botMap, this)
    }

    fun getBotMap(): BotMap {
        return botMapService.getOrCreateBotMap(descriptor.internalName, descriptor.description)
    }

    override fun reviewContracts(ctx: BotContext, contracts: List<Contract>) {
        logger.info("Doing periodic review")
        if (contracts.isEmpty()) return

        val contractIds = contracts.map { it.id }
        val sessions = findByContractIds(contractIds)

        for (session in sessions) {
            logger.info("Reviewing: $session")
            val contract: Contract = contracts.find { it.id == session.contractId } ?: continue

            if (session.endsAt != null && session.endsAt!! < timeSource.nowInUtc()) {
                ctx.addMessage(contract, "Time is up.")
                ctx.release(contract)
            }
        }
    }

    override fun reviewContracts(contracts: List<Contract>) {
        reviewContracts(botContext, contracts)
    }

    fun createInitialPlaceholderSession(did: String, convoId: String): TimerSession {
        val session = TimerSession(
            state = TimerSessionState.CREATED,
            contractSerialNumber = 0,
            createdAt = timeSource.nowInUtc(),
            did = did,
            convoId = convoId,
        )

        timerSessionRepository.save(session)
        return session
    }

    fun getSessionByName(name: String): TimerSession {
        return timerSessionRepository.findByName(name)
    }

    fun processContractForm(form: TimeForm): TimerSession {
        val session = getSessionByName(form.name)

        if (session.state == TimerSessionState.ISSUED) {
            logger.warn("Attempted reprocessing ${form.name}")
            return session
        }

        val token = extractShareableToken(form.shareableToken)
        session.shareableToken = token
        session.isRandom = form.random
        session.isPublic = form.public

        if (form.random) {
            val durationAmount = form.minDuration
            session.minDuration = durationAmount!!
            session.minDurationUnit = form.minUnit
        }

        val maxDurationAmount = form.maxDuration
        session.maxDuration = maxDurationAmount!!
        session.maxDurationUnit = form.maxUnit

        val terms = if (session.isRandom) {
            "Duration revealed on accept"
        } else {
            formatDuration(maxDurationAmount.toLong(), form.maxUnit)
        }

        val contract = botContext.issueContract(
            shareableToken = token,
            terms = "Timer bot: $terms",
            public = session.isPublic,
        )

        session.contractSerialNumber = contract.serialNumber
        session.contractId = contract.id
        session.state = TimerSessionState.ISSUED
        save(session)

        botContext.sendDm(session.convoId ?: "", "Contract issued")

        return session
    }

    fun findByContractIds(contractIds: List<Long>): List<TimerSession> {
        return timerSessionRepository.findByContractIdIn(contractIds)
    }

    fun recordSerialNumberForName(name: String, serialNumber: Int) {
        val session = getSessionByName(name)
        session.contractSerialNumber = serialNumber
        save(session)
    }

    fun save(session: TimerSession): TimerSession {
        return timerSessionRepository.save(session)
    }

    override fun onAccept(ctx: BotContext, contract: Contract) {
        val session = timerSessionRepository.findByContractSerialNumber(contract.serialNumber.toLong())

        session.acceptedAt = timeSource.nowInUtc()

        session.endsAt = if (session.isRandom) {
            val minAmount = session.minDuration
            val minUnit = ChronoUnit.valueOf(session.minDurationUnit)
            val minDuration = Duration.of(minAmount.toLong(), minUnit)

            val maxAmount = session.maxDuration
            val maxUnit = ChronoUnit.valueOf(session.maxDurationUnit)
            val maxDuration = Duration.of(maxAmount.toLong(), maxUnit)

            val minMinutes = minDuration.toMinutes()
            val maxMinutes = maxDuration.toMinutes()

            val randomMinutes = Random.nextLong(minMinutes, maxMinutes + 1)
            session.acceptedAt!!.plus(Duration.ofMinutes(randomMinutes))
        } else {
            val amount = session.maxDuration
            val unit = ChronoUnit.valueOf(session.maxDurationUnit)
            val duration = Duration.of(amount.toLong(), unit)
            session.acceptedAt!!.plus(duration)
        }

        if (session.isRandom) {
            val relativeDuration = PrettyTime().format(session.endsAt)
            ctx.addMessage(contract, "Ends $relativeDuration")
        }

        session.contractId = contract.id
        timerSessionRepository.save(session)
    }

    override fun handleAccept(contract: Contract) {
        onAccept(botContext, contract)
    }

    override fun onRelease(ctx: BotContext, contract: Contract) {
        val sessions = findByContractIds(listOf(contract.id))
        for (session in sessions) {
            session.completed = true
            session.state = TimerSessionState.COMPLETE
            save(session)
        }
    }

    override fun handleRelease(contract: Contract) {
        onRelease(botContext, contract)
    }
}
