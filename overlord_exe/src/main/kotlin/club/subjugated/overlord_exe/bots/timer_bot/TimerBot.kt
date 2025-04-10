package club.subjugated.overlord_exe.bots.timer_bot

import club.subjugated.fb.bots.BotApiMessage
import club.subjugated.fb.bots.GetContractResponse
import club.subjugated.fb.bots.MessagePayload
import club.subjugated.fb.event.EventType
import club.subjugated.fb.event.SignedEvent
import club.subjugated.overlord_exe.bots.timer_bot.events.IssueContract
import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.ContractState
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.encodePublicKeySecp1
import club.subjugated.overlord_exe.util.loadECPublicKeyFromPkcs8
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.nio.ByteBuffer
import java.security.interfaces.ECPublicKey
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Component
class TimerBot(
    private var botMapService: BotMapService,
    private var contractService: ContractService,
    private var timerBotRecordService: TimerBotRecordService,
    private var timeSource: TimeSource,
    private var transactionTemplate: TransactionTemplate
) {
    @Value("\${overlord.coordinator}") val coordinator: String = ""
    @Value("\${overlord.mqtt_broker_uri}") val brokerUri: String = ""

    lateinit var botMap : BotMap
    lateinit var otherClient : MqttClient

    private val botExecutorWatchdogService = Executors.newSingleThreadScheduledExecutor()

    var responseFuture = CompletableDeferred<BotApiMessage>()

    fun createBotApiExecutor(botMap: BotMap): ScheduledExecutorService {
        return Executors.newSingleThreadScheduledExecutor().apply {
            val client = MqttClient(brokerUri, botMap.externalName, null)
            otherClient = client

            val options =
                MqttConnectOptions().apply {
                    isCleanSession = false
                    userName = botMap.externalName
                    password = botMap.password.toCharArray()
                }

            client.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    println("Disconnected: $cause")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    println("\uD83D\uDCE8 Received on $topic: ${message!!.id}")

                    transactionTemplate.execute { status ->
                        try {
                            if (topic!!.contains("_events_")) {
                                val signedEvent = SignedEvent.getRootAsSignedEvent(ByteBuffer.wrap(message.payload))

                                val e = signedEvent.payload!!
                                val commonMetadata = e.metadata!!
                                println("Event -> ${commonMetadata.lockSession} ${commonMetadata.contractSerialNumber}")

                                when (e.eventType) {
                                    EventType.AcceptContract -> {
                                        handleAccept(signedEvent)
                                    }
                                    EventType.ReleaseContract -> {
                                        handleRelease(signedEvent)
                                    }
                                }
                            }

                            if (topic.contains("_api_")) {
                                val botApiMessage = BotApiMessage.getRootAsBotApiMessage(ByteBuffer.wrap(message.payload))
                                responseFuture.complete(botApiMessage)
                            }
                        } catch (ex : Exception) {
                            println("Encountered exception in processing MQTT")
                            ex.printStackTrace()
                            status.setRollbackOnly()
                        }
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            client.connectWithResult(options).also { connResult ->
                val sessionPresent = connResult.sessionPresent
                println("Session present: $sessionPresent")

                // Only subscribe if there's no session already
                if (!sessionPresent) {
                    client.subscribe("bots/inbox_events_${botMap.externalName}")
                    client.subscribe("bots/inbox_api_${botMap.externalName}")
                }
            }
        }
    }

    suspend fun requestContract(botName : String, lockSession: String, serial : UShort, client : MqttClient) : BotApiMessage {
        return withContext(Dispatchers.IO) {
            responseFuture = CompletableDeferred()
            val requestBody = contractService.makeContractRequest(
                botName,
                lockSession,
                serial
            )
            client.publish("coordinator/inbox", MqttMessage(requestBody))
            responseFuture.await()
        }
    }

    suspend fun addMessage(botName : String, contractName : String, message : String, client : MqttClient) : BotApiMessage {
        return withContext(Dispatchers.IO) {
            responseFuture = CompletableDeferred()
            val requestBody = contractService.makeAddMessageToContractMessage(
                botName,
                contractName,
                message
            )
            client.publish("coordinator/inbox", MqttMessage(requestBody))
            responseFuture.await()
        }
    }

    private fun handleAccept(signedEvent: SignedEvent) {
        println("Handled accept")

        val scope = CoroutineScope(Dispatchers.Default)

        val handler = CoroutineExceptionHandler { _, e ->
            println("Unhandled exception: $e")
        }
        scope.async(handler) {
            val e = signedEvent.payload!!
            val commonMetadata = e.metadata!!

            val contractInfo = requestContract(botMap.externalName, commonMetadata.lockSession!!, commonMetadata.contractSerialNumber, otherClient)
            println("Got contract info $contractInfo")

            val response : GetContractResponse = when(contractInfo.payloadType){
                MessagePayload.GetContractResponse -> {
                    val response = GetContractResponse()
                    contractInfo.payload(response)
                    response
                }
                else -> {
                    throw IllegalStateException()
                }
            }

            val contract = contractService.getOrCreateContract(botMap.externalName, commonMetadata.lockSession!!, commonMetadata.contractSerialNumber.toInt())
            contract.state = ContractState.valueOf(response.state!!)
            contract.externalContractName = response.name
            contractService.save(contract)

            val timeInMinutes = 1L
            val endsAt = timeSource.nowInUtc().plusMinutes(timeInMinutes)
            timerBotRecordService.createTimerBotRecord(contract.id, endsAt)

            val response2 = addMessage(botMap.externalName, contract.externalContractName!!, "Your time was decided: $timeInMinutes", otherClient)
            // We don't need to anything with response 2
        }
    }

    private fun handleRelease(signedEvent: SignedEvent) {
        println("Handle release")
        val e = signedEvent.payload!!
        val commonMetadata = e.metadata!!

        val contract = contractService.markReleased(botMap.externalName, commonMetadata.contractSerialNumber.toInt())
        val tbrs = timerBotRecordService.findByContractIds(listOf(contract.id))
        for(record in tbrs) {
            record.completed = true
            timerBotRecordService.save(record)
        }
    }

    @EventListener
    fun handleIssueContractRequest(event: IssueContract) {
        val compressedPublicKey = encodePublicKeySecp1(loadECPublicKeyFromPkcs8(botMap.publicKey!!) as ECPublicKey)
        val commandBytes = contractService.makeCreateContractCommand(botMap.externalName, event.shareableToken, "Timer lock", false, botMap.privateKey!!, compressedPublicKey)
        otherClient.publish("coordinator/inbox", MqttMessage(commandBytes))
    }

    /**
     * The periodic task that reviews (and releases) active contracts.
     */
    private fun reviewContracts() {
        val contracts = contractService.getLiveContractsForBot(botMap.externalName)

        val contractIds = contracts.map { it.id }
        val tbrs = timerBotRecordService.findByContractIds(contractIds)
        for(record in tbrs) {
            if(record.endsAt!! < timeSource.nowInUtc()) {
                val contract = contracts.find { it.id == record.contractId }!!

                val scope = CoroutineScope(Dispatchers.Default)
                val handler = CoroutineExceptionHandler { _, e ->
                    println("Unhandled exception: $e")
                }
                scope.async(handler) {
                    addMessage(botMap.externalName, contract.externalContractName!!, "Time complete", otherClient)
                }

                val releaseCommand = contractService.makeReleaseCommand(botMap.externalName, contract.externalContractName!!, contract.serialNumber, botMap.privateKey!!)
                otherClient.publish("coordinator/inbox", MqttMessage(releaseCommand))
            }
        }

    }

    @PostConstruct
    fun start() {
        println("Starting TimerBot")
        val botMap = botMapService.getBotMap("timer", coordinator)
        this.botMap = botMap

        var executor = createBotApiExecutor(botMap)

        botExecutorWatchdogService.scheduleAtFixedRate({
            if (botExecutorWatchdogService.isTerminated || botExecutorWatchdogService.isShutdown) {
                println("Bot executor stopped unexpectedly, restarting...")
                executor = createBotApiExecutor(botMap)
            }
        }, 1, 5, TimeUnit.SECONDS)

        executor.scheduleAtFixedRate({
            println("Reviewing live contracts")
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