package club.subjugated.overlord_exe.bots.timer_bot

import club.subjugated.fb.event.SignedEvent
import club.subjugated.overlord_exe.bots.bsky_likes.BSkyLikesBot
import club.subjugated.overlord_exe.bots.general.GenericBotRoot
import club.subjugated.overlord_exe.bots.timer_bot.events.IssueContract
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractState
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.encodePublicKeySecp1
import club.subjugated.overlord_exe.util.loadECPublicKeyFromPkcs8
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.processNextEventInCurrentThread
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.ocpsoft.prettytime.PrettyTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.security.interfaces.ECPublicKey
import java.util.concurrent.TimeUnit

@Component
class TimerBot(
    private var botMapService: BotMapService,
    private var contractService: ContractService,
    private var timerBotRecordService: TimerBotRecordService,
    private var timeSource: TimeSource,
    private var transactionTemplate: TransactionTemplate,
    @Value("\${overlord.coordinator}") val coordinator : String,
    @Value("\${overlord.mqtt_broker_uri}") val brokerUri: String,
    private val logger: Logger = LoggerFactory.getLogger(TimerBot::class.java)
) : GenericBotRoot(
    botMapService, contractService, timeSource, transactionTemplate, coordinator, brokerUri
) {

    override fun handleAccept(signedEvent: SignedEvent) {
        logger.info("Handled accept")

        val scope = CoroutineScope(Dispatchers.Default)

        val handler = CoroutineExceptionHandler { _, e ->
            logger.error("Unhandled exception: $e")
        }
        scope.async(handler) {
            val e = signedEvent.payload!!
            val commonMetadata = e.metadata!!

            val contractInfo = requestContract(botMap.externalName, commonMetadata.lockSession!!, commonMetadata.contractSerialNumber, mqttClientRef)
            logger.debug("Got contract info {}", contractInfo)

            val contract = contractService.getOrCreateContract(botMap.externalName, commonMetadata.lockSession!!, commonMetadata.contractSerialNumber.toInt())
            contractService.updateContractWithGetContractResponse(contract, contractInfo)

            val record = timerBotRecordService.updatePlaceHolderWithContractIdAndCalcEnd(contract.serialNumber, contract.id.toLong())
            val relativeTime = PrettyTime().format(record.endsAt)

            val response2 = addMessage(botMap.externalName, contract.externalContractName!!, "Your end time ${record.endsAt} - $relativeTime", mqttClientRef)
            // We don't need to anything with response 2
        }
    }

    override fun handleRelease(signedEvent: SignedEvent) {
        logger.info("Handle release")
        val e = signedEvent.payload!!
        val commonMetadata = e.metadata!!

        val contract = contractService.markReleased(botMap.externalName, commonMetadata.contractSerialNumber.toInt())
        val tbrs = timerBotRecordService.findByContractIds(listOf(contract.id))
        for(record in tbrs) {
            record.completed = true
            timerBotRecordService.save(record)
        }
    }

    override fun handleLock(signedEvent: SignedEvent) {
    }

    override fun handleUnlock(signedEvent: SignedEvent) {
    }

    @EventListener
    fun handleIssueContractRequest(event: IssueContract) {
        val compressedPublicKey = encodePublicKeySecp1(loadECPublicKeyFromPkcs8(botMap.publicKey!!) as ECPublicKey)
        val wrapper = contractService.makeCreateContractCommand(botMap.externalName, event.shareableToken, event.terms, false, botMap.privateKey!!, compressedPublicKey, event.public)

        timerBotRecordService.recordSerialNumberForName(event.recordName, wrapper.contractSerialNumber)

        mqttClientRef.publish("coordinator/inbox", MqttMessage(wrapper.messageBytes))
    }

    /**
     * The periodic task that reviews (and releases) active contracts.
     */
    private fun reviewContracts() {
        val contracts = contractService.getLiveContractsForBot(botMap.externalName)

        val contractIds = contracts.map { it.id }
        val tbrs = timerBotRecordService.findByContractIds(contractIds)

        val scope = CoroutineScope(Dispatchers.Default)
        val handler = CoroutineExceptionHandler { _, e ->
            logger.error("Unhandled exception: $e")
        }

        for(record in tbrs) {
            val contract : Contract = contracts.find { it.id == record.contractId }!!

            scope.async(handler) {
                val contractInfo = requestContract(botMap.externalName, contract.lockSessionToken!!, contract.serialNumber.toUShort(), mqttClientRef)
                if(contractInfo.state == "ABORTED") {
                    logger.info("Aborted contract: ${contract.externalContractName}")
                    contract.state = ContractState.ABORTED
                    contractService.save(contract)
                }
            }

            if(record.endsAt!! < timeSource.nowInUtc()) {
                scope.async(handler) {
                    addMessage(botMap.externalName, contract.externalContractName!!, "Time complete", mqttClientRef)
                }
                val releaseCommand = contractService.makeReleaseCommand(botMap.externalName, contract.externalContractName!!, contract.serialNumber, botMap.privateKey!!)
                mqttClientRef.publish("coordinator/inbox", MqttMessage(releaseCommand))
            }
        }
    }

    @PostConstruct
    fun start() {
        logger.info("Starting TimerBot")
        val botMap = botMapService.getOrCreateBotMap("timer", "TimerBot", coordinator)
        this.botMap = botMap

        var executor = createBotApiExecutor(botMap)

        botExecutorWatchdogService.scheduleAtFixedRate({
            if (botExecutorWatchdogService.isTerminated || botExecutorWatchdogService.isShutdown) {
                logger.warn("Bot executor stopped unexpectedly, restarting...")
                executor = createBotApiExecutor(botMap)
            }
        }, 1, 5, TimeUnit.SECONDS)

        executor.scheduleAtFixedRate({
            logger.info("Reviewing live contracts")
            try {
                reviewContracts()
            } catch (ex : Exception ) {
                println(ex)
            }

        }, 0, 1, TimeUnit.MINUTES)
    }

    @PreDestroy
    fun stop() {
        botExecutorWatchdogService.shutdown()
    }
}