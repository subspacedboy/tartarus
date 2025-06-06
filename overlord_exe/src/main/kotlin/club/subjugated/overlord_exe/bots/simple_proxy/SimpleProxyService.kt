package club.subjugated.overlord_exe.bots.simple_proxy

import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.bots.general.MessageHandler
import club.subjugated.overlord_exe.bots.superbot.SuperBotService
import club.subjugated.overlord_exe.events.IssueContract
import club.subjugated.overlord_exe.events.IssueLock
import club.subjugated.overlord_exe.events.IssueRelease
import club.subjugated.overlord_exe.events.IssueUnlock
import club.subjugated.overlord_exe.models.BSkyUser
import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.util.TimeSource
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class SimpleProxyService(
    private val simpleProxyRepository: SimpleProxyRepository,
    private val botMapService: BotMapService,
    private val botComponent: BotComponent,
    private val logger: Logger = LoggerFactory.getLogger(SimpleProxyService::class.java),
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val contractService: ContractService,
    private val timeSource: TimeSource
) : MessageHandler {
    @PostConstruct
    fun start() {
        logger.info("Starting SimpleProxy")
        val botMap = getBotMap()
        botComponent.startBot(botMap, this)
    }

    fun getBotMap() : BotMap {
        return botMapService.getOrCreateBotMap("simple_proxy", "Simple proxy")
    }

    fun save(simpleProxy: SimpleProxy) : SimpleProxy {
        return simpleProxyRepository.save(simpleProxy)
    }

    fun findByContractSerialNumber(serialNumber : Long) : SimpleProxy {
        return simpleProxyRepository.findByContractSerialNumber(serialNumber)
    }

    fun issueContract(keyHolder : BSkyUser, sub : BSkyUser) {
        val record = SimpleProxy(
            contractSerialNumber = 0,
            bskyUser = sub,
            keyHolderBskyUser = keyHolder,
            state = SimpleProxyState.ISSUED,
            createdAt = timeSource.nowInUtc(),
            updatedAt = timeSource.nowInUtc()
        )

        applicationEventPublisher.publishEvent(
            IssueContract(
                source = this,
                botMap = getBotMap(),
                serialNumberRecorder = {serial ->
                    record.contractSerialNumber = serial
                    save(record)
                },
                public = false,
                shareableToken = sub.shareableToken!!,
                recordName = record.name,
                terms = "Proxy for ${keyHolder.handle}"
            )
        )
    }

    override fun reviewContracts(contracts: List<Contract>) {
        // No op. Nothing to review.
    }

    override fun handleAccept(contract: Contract) {
        val record = findByContractSerialNumber(contract.serialNumber.toLong())
        record.state = SimpleProxyState.ACCEPTED
        record.contractId = contract.id
        save(record)
    }

    override fun handleRelease(contract: Contract) {
        val record = findByContractSerialNumber(contract.serialNumber.toLong())
        record.state = SimpleProxyState.RELEASED
        save(record)
    }

    override fun handleLock(contract: Contract) {
        // No op
    }

    override fun handleUnlock(contract: Contract) {
        // No op
    }

    fun release(keyHolder : BSkyUser, sub : BSkyUser) {
        val proxies = simpleProxyRepository.findByBskyUserAndKeyHolderBskyUserAndStateIn(sub, keyHolder, listOf(
            SimpleProxyState.ACCEPTED))

        val botMap = getBotMap()
        proxies.forEach { p ->
            val contract = contractService.getContract(botMap.externalName, p.contractSerialNumber.toInt())

            applicationEventPublisher.publishEvent(IssueRelease(
                source = this,
                botMap = botMap,
                contract = contract,
            ))
        }
    }

    fun unlock(keyHolder : BSkyUser, sub : BSkyUser) {
        val proxies = simpleProxyRepository.findByBskyUserAndKeyHolderBskyUserAndStateIn(sub, keyHolder, listOf(
            SimpleProxyState.ACCEPTED))

        val botMap = getBotMap()
        proxies.forEach { p ->
            val contract = contractService.getContract(botMap.externalName, p.contractSerialNumber.toInt())

            val scope = CoroutineScope(Dispatchers.Default)
            val exceptionHandler = CoroutineExceptionHandler { _, e ->
                logger.error("Unhandled exception: $e")
            }

            scope.async(exceptionHandler) {
                val response = botComponent.requestContract(botMap.externalName, contract.lockSessionToken!!, contract.serialNumber.toUShort(), botComponent.botsToClients[botMap]!!)
                contract.nextCounter = response.nextCounter.toInt()

                applicationEventPublisher.publishEvent(IssueUnlock(
                    source = this,
                    botMap = botMap,
                    contract = contract,
                ))
            }
        }
    }

    fun lock(keyHolder : BSkyUser, sub : BSkyUser) {
        val proxies = simpleProxyRepository.findByBskyUserAndKeyHolderBskyUserAndStateIn(sub, keyHolder, listOf(
            SimpleProxyState.ACCEPTED))

        val botMap = getBotMap()
        proxies.forEach { p ->
            val contract = contractService.getContract(botMap.externalName, p.contractSerialNumber.toInt())

            val scope = CoroutineScope(Dispatchers.Default)
            val exceptionHandler = CoroutineExceptionHandler { _, e ->
                logger.error("Unhandled exception: $e")
            }

            scope.async(exceptionHandler) {
                val response = botComponent.requestContract(botMap.externalName, contract.lockSessionToken!!, contract.serialNumber.toUShort(), botComponent.botsToClients[botMap]!!)
                contract.nextCounter = response.nextCounter.toInt()

                applicationEventPublisher.publishEvent(IssueLock(
                    source = this,
                    botMap = botMap,
                    contract = contract,
                ))
            }
        }
    }
}