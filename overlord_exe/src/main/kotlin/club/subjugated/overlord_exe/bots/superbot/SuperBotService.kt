package club.subjugated.overlord_exe.bots.superbot

import club.subjugated.overlord_exe.bots.superbot.web.IntakeForm
import club.subjugated.overlord_exe.components.SendDmEvent
import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.util.TimeSource
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class SuperBotService(
    private val superBotRecordRepository: SuperBotRecordRepository,
    private val contractService: ContractService,
    private val timeSource: TimeSource,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val botMapService: BotMapService
) {

    fun getBotMap() : BotMap {
        return botMapService.getOrCreateBotMap("superBot", "OverLord SuperBot")
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
            convoId = record.convoId!!,
            did = record.did!!
        ))
    }
}