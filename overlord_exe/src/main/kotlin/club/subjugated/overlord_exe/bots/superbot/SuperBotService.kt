package club.subjugated.overlord_exe.bots.superbot

import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.bots.general.MessageHandler
import club.subjugated.overlord_exe.bots.superbot.web.IntakeForm
import club.subjugated.overlord_exe.bots.timer_bot.TimerBotRecordService
import club.subjugated.overlord_exe.components.SendDmEvent
import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.util.TimeSource
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class SuperBotService(
    private val superBotRecordRepository: SuperBotRecordRepository,
    private val contractService: ContractService,
    private val timeSource: TimeSource,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val botMapService: BotMapService,
    private val botComponent: BotComponent,
    private val logger: Logger = LoggerFactory.getLogger(SuperBotService::class.java)
) : MessageHandler {
    @PostConstruct
    fun start() {
        logger.info("Starting SuperBot")
        val botMap = getBotMap()
        botComponent.startBot(botMap, this)
    }

    fun getBotMap() : BotMap {
        return botMapService.getOrCreateBotMap("superBot", "OverLord SuperBot")
    }

    override fun reviewContracts(contracts: List<Contract>) {

    }

    override fun handleAccept(contract: Contract) {

    }

    override fun handleRelease(contract: Contract) {

    }

    override fun handleLock(contract: Contract) {

    }

    override fun handleUnlock(contract: Contract) {

    }

    fun createPlaceholder(did : String, convoId : String) : String {
        val record = SuperBotRecord(
            contractSerialNumber = 0,
            state = SuperBotRecordState.CREATED,
            did = did,
            convoId = convoId,
            createdAt = timeSource.nowInUtc()
        )
        superBotRecordRepository.save(record)
        return record.name
    }

    fun getRecord(name : String) : SuperBotRecord {
        return superBotRecordRepository.findByName(name)
    }

    fun save(record : SuperBotRecord) {
        superBotRecordRepository.save(record)
    }

    fun processContractForm(form : IntakeForm) {
        val record = getRecord(form.name)

        record.shareableToken = form.shareableToken
        record.state = SuperBotRecordState.ISSUED
        save(record)

        applicationEventPublisher.publishEvent(
            club.subjugated.overlord_exe.events.IssueContract(
                source = this,
                botMap = getBotMap(),
                serialNumberRecorder = {serial ->
                    record.contractSerialNumber = serial
                    save(record)
                },
                recordName = record.name,
                shareableToken = form.shareableToken,
                public = form.public,
                terms = "Superbot stuff"
            )
        )

        applicationEventPublisher.publishEvent(SendDmEvent(
            source = this,
            message = "Contract issued",
            convoId = record.convoId,
            did = record.did
        ))
    }
}