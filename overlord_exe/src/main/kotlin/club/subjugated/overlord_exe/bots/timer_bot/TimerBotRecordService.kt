package club.subjugated.overlord_exe.bots.timer_bot

import club.subjugated.overlord_exe.bots.bsky_likes.BSkyLikeBotRecord
import club.subjugated.overlord_exe.bots.bsky_selflock.events.IssueContract
import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.bots.general.MessageHandler
import club.subjugated.overlord_exe.bots.timer_bot.web.TimeForm
import club.subjugated.overlord_exe.components.SendDmEvent
import club.subjugated.overlord_exe.events.AddMessageToContract
import club.subjugated.overlord_exe.events.IssueRelease
import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractState
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.extractShareableToken
import club.subjugated.overlord_exe.util.formatDuration
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.ocpsoft.prettytime.PrettyTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlin.random.Random

@Service
class TimerBotRecordService(
    private val timerBotRecordRepository: TimerBotRecordRepository,
    private val timeSource: TimeSource,
    private val applicationEventPublisher : ApplicationEventPublisher,
    private val botMapService: BotMapService,
    private val botComponent: BotComponent
) : MessageHandler {
    private val logger: Logger = LoggerFactory.getLogger(TimerBotRecordService::class.java)

    @PostConstruct
    fun start() {
        logger.info("Starting TimerBot")
        val botMap = getBotMap()
        botComponent.startBot(botMap, this)
    }

    fun getBotMap() : BotMap {
        return botMapService.getOrCreateBotMap("timer", "TimerBot")
    }

    override fun reviewContracts(contracts : List<Contract>) {
        logger.info("Doing periodic review")

        val contractIds = contracts.map { it.id }
        val tbrs = findByContractIds(contractIds)

        for(record in tbrs) {
            logger.info("Reviewing: $record")
            val contract : Contract = contracts.find { it.id == record.contractId }!!

            if(record.endsAt!! < timeSource.nowInUtc()) {
                applicationEventPublisher.publishEvent(AddMessageToContract(
                    source = this,
                    botMap = getBotMap(),
                    contract = contract,
                    message = "Time is up."
                ))

                applicationEventPublisher.publishEvent(IssueRelease(
                    source = this,
                    botMap = getBotMap(),
                    contract = contract,
                ))
            }
        }
    }

    fun createInitialPlaceholderRecord(did: String, convoId: String) : TimerBotRecord {
        val record = TimerBotRecord(
            state = TimerBotRecordState.CREATED,
            contractSerialNumber = 0,
            createdAt = timeSource.nowInUtc(),
            did = did,
            convoId = convoId
        )

        timerBotRecordRepository.save(record)
        return record
    }

    fun getRecordByName(name : String) : TimerBotRecord {
        return timerBotRecordRepository.findByName(name)
    }

    fun processContractForm(form : TimeForm) : TimerBotRecord {
        val record = getRecordByName(form.name)

        val token = extractShareableToken(form.shareableToken)
        record.shareableToken = token
        record.isRandom = form.random

        if(form.random) {
            val durationAmount = form.minDuration
            val durationUnit = ChronoUnit.valueOf(form.minUnit)
            val minimumDuration = Duration.of(durationAmount!!.toLong(), durationUnit)
            record.minDuration = durationAmount
            record.minDurationUnit = form.minUnit

        }

        val maxDurationAmount = form.maxDuration
        val maxDurationUnit = ChronoUnit.valueOf(form.maxUnit)
        val maximumDuration = Duration.of(maxDurationAmount!!.toLong(), maxDurationUnit)
        record.maxDuration = maxDurationAmount
        record.minDurationUnit = form.maxUnit

        record.state = TimerBotRecordState.ISSUED
        save(record)

        val terms = if(record.isRandom) {
            "Duration revealed on accept"
        } else {
            formatDuration(maxDurationAmount, form.maxUnit)
        }

        // Issue the contract
        applicationEventPublisher.publishEvent(
            club.subjugated.overlord_exe.events.IssueContract(
                source = this,
                botMap = getBotMap(),
                serialNumberRecorder = {serial ->
                    record.contractSerialNumber = serial
                    save(record)
                },
                recordName = record.name,
                shareableToken = token,
                public = record.isPublic,
                terms = "Timer bot: $terms"
            )
        )

        applicationEventPublisher.publishEvent(SendDmEvent(
            source = this,
            message = "Contract issued",
            convoId = record.convoId!!,
            did = record.did!!
        ))

        return record
    }

    fun findByContractIds(contractIds : List<Long>) : List<TimerBotRecord> {
        return timerBotRecordRepository.findByContractIdIn(contractIds)
    }

    fun recordSerialNumberForName(name: String, serialNumber : Int) {
        val record = getRecordByName(name)
        record.contractSerialNumber = serialNumber
        save(record)
    }

    fun save(record: TimerBotRecord) : TimerBotRecord {
        return timerBotRecordRepository.save(record)
    }

    override fun handleAccept(contract: Contract) {
        val record = timerBotRecordRepository.findByContractSerialNumber(contract.serialNumber.toLong())

        record.acceptedAt = timeSource.nowInUtc()

        record.endsAt = if(record.isRandom) {
            val minAmount = record.minDuration
            val minUnit = ChronoUnit.valueOf(record.minDurationUnit)
            val minDuration = Duration.of(minAmount.toLong(), minUnit)

            val maxAmount = record.maxDuration
            val maxUnit = ChronoUnit.valueOf(record.maxDurationUnit)
            val maxDuration = Duration.of(maxAmount.toLong(), maxUnit)

            val minMinutes = minDuration.toMinutes()
            val maxMinutes = maxDuration.toMinutes()

            val randomMinutes = Random.nextLong(minMinutes, maxMinutes + 1)
            record.acceptedAt!!.plus(Duration.ofMinutes(randomMinutes))
        } else {
            val amount = record.maxDuration
            val unit = ChronoUnit.valueOf(record.maxDurationUnit)
            val duration = Duration.of(amount.toLong(), unit)
            record.acceptedAt!!.plus(duration)
        }

        if(record.isRandom) {
            val relativeDuration = PrettyTime().format(record.endsAt)
            applicationEventPublisher.publishEvent(AddMessageToContract(
                source = this,
                botMap = getBotMap(),
                contract = contract,
                message = "Ends $relativeDuration"
            ))
        }

        record.contractId = contract.id

        timerBotRecordRepository.save(record)
    }

    override fun handleRelease(contract: Contract) {
        val tbrs = findByContractIds(listOf(contract.id))
        for(record in tbrs) {
            record.completed = true
            record.state = TimerBotRecordState.COMPLETE
            save(record)
        }
    }

    override fun handleLock(contract: Contract) {
        // Nothing to do
    }

    override fun handleUnlock(contract: Contract) {
        // Nothing to do
    }
}