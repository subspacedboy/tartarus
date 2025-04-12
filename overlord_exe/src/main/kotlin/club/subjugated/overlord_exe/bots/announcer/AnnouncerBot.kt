package club.subjugated.overlord_exe.bots.announcer

import club.subjugated.fb.event.SignedEvent
import club.subjugated.overlord_exe.bots.announcer.events.ConnectIdentityEvent
import club.subjugated.overlord_exe.bots.general.GenericBotRoot
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.util.TimeSource
import io.ktor.util.moveToByteArray
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.TimeUnit

@Component
class AnnouncerBot(
    private var botMapService: BotMapService,
    private var contractService: ContractService,
    private var timeSource: TimeSource,
    private var transactionTemplate: TransactionTemplate,
    private var blueSkyService: BlueSkyService,
    private var announcerRecordService: AnnouncerRecordService,
    @Value("\${overlord.coordinator}") val coordinator : String,
    @Value("\${overlord.mqtt_broker_uri}") val brokerUri: String,
) : GenericBotRoot(
    botMapService, contractService, timeSource, transactionTemplate, coordinator, brokerUri
) {
    override fun handleAccept(signedEvent: SignedEvent) {
        val scope = CoroutineScope(Dispatchers.Default)

        val handler = CoroutineExceptionHandler { _, e ->
            println("Unhandled exception: $e")
        }
        scope.async(handler) {
            val e = signedEvent.payload!!
            val commonMetadata = e.metadata!!

            val contractInfo = requestContract(
                botMap.externalName,
                commonMetadata.lockSession!!,
                commonMetadata.contractSerialNumber,
                otherClient
            )

            val message = announcerRecordService.generateMessageForAccept(contractInfo.shareableToken!!, contractInfo.authorName!!, contractInfo.signedMessageAsByteBuffer.moveToByteArray())
            blueSkyService.post(message)
        }
    }

    override fun handleRelease(signedEvent: SignedEvent) {
        val scope = CoroutineScope(Dispatchers.Default)

        val handler = CoroutineExceptionHandler { _, e ->
            println("Unhandled exception: $e")
        }
        scope.async(handler) {
            val e = signedEvent.payload!!
            val commonMetadata = e.metadata!!

            val contractInfo = requestContract(
                botMap.externalName,
                commonMetadata.lockSession!!,
                commonMetadata.contractSerialNumber,
                otherClient
            )

            val message = announcerRecordService.generateMessageForRelease(contractInfo.shareableToken!!)
            blueSkyService.post(message)
        }
    }

    override fun handleLock(signedEvent: SignedEvent) {
    }

    override fun handleUnlock(signedEvent: SignedEvent) {
    }

    @PostConstruct
    fun start() {
        println("Starting Announcer Bot")
        val botMap = botMapService.getOrCreateBotMap("announcer", "Announcer bot", coordinator)
        this.botMap = botMap

        var executor = createBotApiExecutor(botMap)

        botExecutorWatchdogService.scheduleAtFixedRate({
            if (botExecutorWatchdogService.isTerminated || botExecutorWatchdogService.isShutdown) {
                println("Bot executor stopped unexpectedly, restarting...")
                executor = createBotApiExecutor(botMap)
            }
        }, 1, 5, TimeUnit.SECONDS)
    }

    @EventListener
    fun handleConnectIdentity(event: ConnectIdentityEvent) {
        announcerRecordService.createRecord(event.token, event.did)
    }
}