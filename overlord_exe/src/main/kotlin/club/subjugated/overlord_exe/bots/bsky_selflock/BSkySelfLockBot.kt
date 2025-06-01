package club.subjugated.overlord_exe.bots.bsky_selflock

import club.subjugated.fb.event.SignedEvent
import club.subjugated.overlord_exe.bots.general.GenericBotRoot
import club.subjugated.overlord_exe.bots.timer_bot.TimerBotRecordService
import club.subjugated.overlord_exe.events.IssueContract
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
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.security.interfaces.ECPublicKey
import java.util.concurrent.TimeUnit

@Component
class BSkySelfLockBot(
    private var botMapService: BotMapService,
    private var contractService: ContractService,
    private var bSkySelfLockService: BSkySelfLockService,
    private var timeSource: TimeSource,
    private var transactionTemplate: TransactionTemplate,
    @Value("\${overlord.coordinator}") val coordinator : String,
    @Value("\${overlord.mqtt_broker_uri}") val brokerUri: String,
    private val logger: Logger = LoggerFactory.getLogger(BSkySelfLockBot::class.java)
) : GenericBotRoot(
    botMapService, contractService, timeSource, transactionTemplate, coordinator, brokerUri
) {
    override fun handleAccept(signedEvent: SignedEvent) {
        logger.info("Contract accepted")

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

            // Create the public post
            bSkySelfLockService.processAccept(commonMetadata.contractSerialNumber.toInt(), contract)
        }
    }

    override fun handleRelease(signedEvent: SignedEvent) {
        logger.info("Handle release")
        val e = signedEvent.payload!!
        val commonMetadata = e.metadata!!

        val contract = contractService.markReleased(botMap.externalName, commonMetadata.contractSerialNumber.toInt())
        val tbrs = bSkySelfLockService.findByContractIds(listOf(contract.id))
        for(record in tbrs) {
            record.completed = true
            record.state = BSkySelfLockRecordState.COMPLETE
            bSkySelfLockService.saveRecord(record)
        }
    }

    override fun handleLock(signedEvent: SignedEvent) {
    }

    override fun handleUnlock(signedEvent: SignedEvent) {
    }

    private fun reviewContracts() {
        val contracts = contractService.getLiveContractsForBot(botMap.externalName)

        val contractIds = contracts.map { it.id }
        val tbrs = bSkySelfLockService.findByContractIds(contractIds)

        val scope = CoroutineScope(Dispatchers.Default)
        val handler = CoroutineExceptionHandler { _, e ->
            logger.error("Unhandled exception: $e")
        }

        for(record in tbrs) {
            val contract : Contract = contracts.find { it.id == record.contractId }!!

            if(record.state == BSkySelfLockRecordState.OPEN_POSTED) {
                bSkySelfLockService.checkIfNoticeReposted(record)
            }

            if(record.state == BSkySelfLockRecordState.IN_OPEN) {
                bSkySelfLockService.checkIfOpenEndedAndCalculate(record)
            }

            if(record.state == BSkySelfLockRecordState.CLOSED) {
                if(timeSource.nowInUtc() > record.endsAt) {
                    val releaseCommand = contractService.makeReleaseCommand(botMap.externalName, contract.externalContractName!!, contract.serialNumber, botMap.privateKey!!)
                    mqttClientRef.publish("coordinator/inbox", MqttMessage(releaseCommand))
                }
            }

            scope.async(handler) {
                val contractInfo = requestContract(botMap.externalName, contract.lockSessionToken!!, contract.serialNumber.toUShort(), mqttClientRef)
                if(contractInfo.state == "ABORTED") {
                    logger.info("Aborted contract: ${contract.externalContractName}")
                    contract.state = ContractState.ABORTED
                    contractService.save(contract)
                }
            }

        }
    }

    @EventListener
    fun handleIssueContractRequest(event: club.subjugated.overlord_exe.bots.bsky_selflock.events.IssueContract) {
        val compressedPublicKey = encodePublicKeySecp1(loadECPublicKeyFromPkcs8(botMap.publicKey!!) as ECPublicKey)
        val wrapper = contractService.makeCreateContractCommand(botMap.externalName, event.shareableToken, "BSky Self Lock per terms", false, botMap.privateKey!!, compressedPublicKey, true)

        bSkySelfLockService.recordSerialNumberForName(event.name, wrapper.contractSerialNumber)

        mqttClientRef.publish("coordinator/inbox", MqttMessage(wrapper.messageBytes))
    }

    @PostConstruct
    fun start() {
        logger.info("Starting BSky Self Lock")
        val botMap = botMapService.getOrCreateBotMap("bsky_selflock", "BSky Self Lock")
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

        }, 0, 3, TimeUnit.MINUTES)
    }

    @PreDestroy
    fun stop() {
        botExecutorWatchdogService.shutdown()
    }
}