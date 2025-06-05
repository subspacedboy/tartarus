package club.subjugated.overlord_exe.bots.announcer

import club.subjugated.fb.message.Contract
import club.subjugated.fb.message.SignedMessage
import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.bots.general.MessageHandler
import club.subjugated.overlord_exe.bots.timer_bot.TimerBotRecordService
import club.subjugated.overlord_exe.models.BSkyUser
import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.services.BSkyUserService
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.util.TimeSource
import io.ktor.util.moveToByteArray
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import kotlin.contracts.contract

@Service
class AnnouncerRecordService(
    private val botMapService: BotMapService,
    private val botComponent: BotComponent,
    private val bskyUserService: BSkyUserService,
    private val timeSource: TimeSource,
    private val blueSkyService: BlueSkyService,
    private val logger: Logger = LoggerFactory.getLogger(AnnouncerRecordService::class.java)
) : MessageHandler {

    @PostConstruct
    fun start() {
        logger.info("Starting Announcer")
        val botMap = getBotMap()
        botComponent.startBot(botMap, this)
    }

    fun getBotMap() : BotMap {
        return botMapService.getOrCreateBotMap("announcer", "Announcer bot")
    }

    fun createRecord(token : String, did : String) : BSkyUser {
        val record = bskyUserService.findOrCreateByDid(did)
        record.shareableToken = token
        bskyUserService.save(record)

        return record
    }

    fun generateMessageForAccept(shareableToken: String, contract: club.subjugated.overlord_exe.models.Contract) : String {
        val subject = bskyUserService.findByShareableToken(shareableToken)?.let {
            blueSkyService.resolveDidToHandle(it.did)
        } ?: "Unknown"

        val terms = contract.signedMessage?.let {
            val t = parseTerms(it)
            " with terms: ${t}."
        } ?: "."


        return "$subject has accepted a contract$terms".take(300)
    }

    fun generateMessageForRelease(shareableToken: String) : String {
        val subject = bskyUserService.findByShareableToken(shareableToken)?.let {
            blueSkyService.resolveDidToHandle(it.did)
        } ?: "Unknown"

        return "$subject has been released".take(300)
    }

    private fun parseTerms(signedMessage : ByteArray) : String {
        val byteBuffer = ByteBuffer.wrap(signedMessage)

        // Parse the FlatBuffer object
        val sm = SignedMessage.getRootAsSignedMessage(byteBuffer)
        val contract = Contract()
        sm.payload(contract)

        return contract.terms!!
    }

    override fun reviewContracts(contracts: List<club.subjugated.overlord_exe.models.Contract>) {
        // No op
    }

    override fun handleAccept(contract: club.subjugated.overlord_exe.models.Contract) {
        val message = generateMessageForAccept(contract.shareableToken!!, contract)
        blueSkyService.post(message)
    }

    override fun handleRelease(contract: club.subjugated.overlord_exe.models.Contract) {
        val message = generateMessageForRelease(contract.shareableToken!!)
        blueSkyService.post(message)
    }

    override fun handleLock(contract: club.subjugated.overlord_exe.models.Contract) {
        // TODO
    }

    override fun handleUnlock(contract: club.subjugated.overlord_exe.models.Contract) {
        // TODO
    }
}