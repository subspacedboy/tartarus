package club.subjugated.overlord_exe.bots.superbot

import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.bots.general.MessageHandler
import club.subjugated.overlord_exe.bots.superbot.web.IntakeForm
import club.subjugated.overlord_exe.events.IssueContract
import club.subjugated.overlord_exe.events.IssueRelease
import club.subjugated.overlord_exe.events.SendDmEvent
import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.StateMachineState
import club.subjugated.overlord_exe.services.BSkyUserService
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.StateMachineService
import club.subjugated.overlord_exe.util.TimeSource
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class SuperBotService(
    private val superBotRecordRepository: SuperBotRecordRepository,
    private val timeSource: TimeSource,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val botMapService: BotMapService,
    private val botComponent: BotComponent,
    private val logger: Logger = LoggerFactory.getLogger(SuperBotService::class.java),
    private val stateMachineService: StateMachineService,
    private val bSkyUserService: BSkyUserService
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

    fun createPlaceholder(did : String, convoId : String) : String {
        val user = bSkyUserService.findOrCreateByDid(did)

        val record = SuperBotRecord(
            contractSerialNumber = 0,
            state = SuperBotRecordState.CREATED,
            did = did,
            convoId = convoId,
            createdAt = timeSource.nowInUtc(),
            shareableToken = user.shareableToken
        )
        superBotRecordRepository.save(record)

        return record.name
    }

    fun processContractForm(form : IntakeForm) {
        val record = findByName(form.name)

        record.shareableToken = form.shareableToken
        record.state = SuperBotRecordState.ISSUED
        record.configuration.objectives = form.objectives
        save(record)

        applicationEventPublisher.publishEvent(
            IssueContract(
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

    override fun reviewContracts(contracts: List<Contract>) {
        logger.info("Going through superbot records")

        contracts.forEach { c ->
            val machines = stateMachineService.findByOwnedBy(c.name)
            machines.forEach { machine ->
                if(machine.state == StateMachineState.STARTED) {
                    stateMachineService.process(machine)
                    stateMachineService.save(machine)
                }
            }

            val allDone = machines.all { it.state == StateMachineState.COMPLETE }
            if(allDone && machines.isNotEmpty()) {
                applicationEventPublisher.publishEvent(IssueRelease(
                    source = this,
                    botMap = getBotMap(),
                    contract = c,
                ))
            }
        }
    }

    override fun handleAccept(contract: Contract) {
        val record = findByContractSerialNumber(contract.serialNumber.toLong())
        record.acceptedAt = timeSource.nowInUtc()
        record.contractId = contract.id
        superBotRecordRepository.save(record)

        val objective = record.chooseObjective()

        val stateMachine = stateMachineService.createNewStateMachine(
            ownerName = contract.name,
            form = objective.first,
            providerClassName = objective.second,
        )

        val bskyUser = bSkyUserService.findOrCreateByDid(record.did)
        stateMachine.bskyUser = bskyUser
        stateMachineService.save(stateMachine)

        stateMachineService.process(stateMachine)
        stateMachineService.save(stateMachine)
    }

    override fun handleRelease(contract: Contract) {
        val record = findByContractSerialNumber(contract.serialNumber.toLong())
        record.state = SuperBotRecordState.COMPLETE
        save(record)
    }

    override fun handleLock(contract: Contract) {

    }

    override fun handleUnlock(contract: Contract) {

    }

    fun findByName(name : String) : SuperBotRecord {
        return superBotRecordRepository.findByName(name)
    }

    fun save(record : SuperBotRecord) {
        superBotRecordRepository.save(record)
    }

    fun findByContractSerialNumber(serialNumber : Long) : SuperBotRecord {
        return superBotRecordRepository.findByContractSerialNumber(serialNumber)
    }
}