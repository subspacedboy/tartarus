package club.subjugated.overlord_exe.bots.bsky_likes

import club.subjugated.fb.event.SignedEvent
import club.subjugated.overlord_exe.bots.general.GenericBotRoot
import club.subjugated.overlord_exe.models.ContractState
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.encodePublicKeySecp1
import club.subjugated.overlord_exe.util.loadECPublicKeyFromPkcs8
import jakarta.annotation.PostConstruct
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
class BSkyLikesBot(
    private var botMapService: BotMapService,
    private var contractService: ContractService,
    private var timeSource: TimeSource,
    private var transactionTemplate: TransactionTemplate,
    private var bSkyLikesBotService: BSkyLikesBotService,
    private var blueSkyService: BlueSkyService,
    @Value("\${overlord.coordinator}") val coordinator : String,
    @Value("\${overlord.mqtt_broker_uri}") val brokerUri: String,
    private val logger: Logger = LoggerFactory.getLogger(BSkyLikesBot::class.java)
) : GenericBotRoot(
    botMapService, contractService, timeSource, transactionTemplate, coordinator, brokerUri
) {
    override fun handleAccept(signedEvent: SignedEvent) {
        val scope = CoroutineScope(Dispatchers.Default)

        val handler = CoroutineExceptionHandler { _, e ->
            logger.error("Unhandled exception: $e")
        }
        scope.async(handler) {
            val e = signedEvent.payload!!
            val commonMetadata = e.metadata!!

            val contractInfo = requestContract(botMap.externalName, commonMetadata.lockSession!!, commonMetadata.contractSerialNumber, mqttClientRef)
            println("Got contract info $contractInfo")

            val contract = contractService.getOrCreateContract(botMap.externalName, commonMetadata.lockSession!!, commonMetadata.contractSerialNumber.toInt())
            contractService.updateContractWithGetContractResponse(contract, contractInfo)

            val record = bSkyLikesBotService.updatePlaceHolderWithContractId(contract.serialNumber, contract.id)

            val response2 = addMessage(botMap.externalName, contract.externalContractName!!, "Good luck on your goal of ${record.goal} likes", mqttClientRef)
            // We don't need to anything with response 2
        }
    }

    override fun handleRelease(signedEvent: SignedEvent) {
        logger.info("Handle release")
        val e = signedEvent.payload!!
        val commonMetadata = e.metadata!!
        markReleased(commonMetadata.contractSerialNumber.toInt())
    }

    override fun handleLock(signedEvent: SignedEvent) {
    }

    override fun handleUnlock(signedEvent: SignedEvent) {
    }

    fun reviewContracts() {
        val contracts = contractService.getLiveContractsForBot(botMap.externalName)

        val contractIds = contracts.map { it.id }
        val records = bSkyLikesBotService.findByContractIds(contractIds)
        for(r in records) {
            logger.debug("Reviewing ${r.name}")

            var totalLikes = 0L
            blueSkyService.getAuthorFeedFromTime(r.did, r.acceptedAt!!) { post ->
                blueSkyService.traceThread(post.uri!!) { post2 ->
                    if(post2.author?.did == r.did) {
                        if(post2.embed?.asImages != null) {
                            val imageEmbed = post2.embed!!.asImages!!
                            totalLikes += post2.likeCount!!
                        }
                    }
                }
            }
            r.likesSoFar = totalLikes
            if(r.likesSoFar >= r.goal) {
                r.completed = true
                var contract = contractService.getContract(botMap.externalName, r.contractSerialNumber)
                releaseContract(contract)
            }
            bSkyLikesBotService.save(r)

            logger.debug("Total likes: $totalLikes of ${r.goal}")
        }
    }

    @PostConstruct
    fun start() {
        logger.info("Starting BSky Likes Bot")
        val botMap = botMapService.getOrCreateBotMap("bsky_likes", "BlueSky likes bot")
        this.botMap = botMap

        var executor = createBotApiExecutor(botMap)

        botExecutorWatchdogService.scheduleAtFixedRate({
            if (botExecutorWatchdogService.isTerminated || botExecutorWatchdogService.isShutdown) {
                println("Bot executor stopped unexpectedly, restarting...")
                executor = createBotApiExecutor(botMap)
            }
        }, 1, 5, TimeUnit.SECONDS)

        executor.scheduleAtFixedRate({
            logger.info("BSky - Reviewing live contracts")
            try {
                reviewContracts()
            } catch (ex : Exception ) {
                println(ex)
            }

        }, 0, 2, TimeUnit.MINUTES)
    }

    @EventListener
    fun handleIssueContractRequest(event: club.subjugated.overlord_exe.bots.bsky_likes.events.IssueContract) {
        val compressedPublicKey = encodePublicKeySecp1(loadECPublicKeyFromPkcs8(botMap.publicKey!!) as ECPublicKey)
        val wrapper = contractService.makeCreateContractCommand(botMap.externalName, event.shareableToken, "BSky Likes - Goal ${event.goal}", false, botMap.privateKey!!, compressedPublicKey, true)
        bSkyLikesBotService.createInitialPlaceholderRecord(wrapper.contractSerialNumber, event.did, event.goal)
        mqttClientRef.publish("coordinator/inbox", MqttMessage(wrapper.messageBytes))
    }
}