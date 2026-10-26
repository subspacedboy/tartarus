package club.subjugated.overlord_exe.bots.simple_proxy

import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.bots.simple_proxy.web.SimpleProxyIntakeForm
import club.subjugated.overlord_exe.models.BSkyUser
import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.runtime.BotContext
import club.subjugated.overlord_exe.runtime.BotDescriptor
import club.subjugated.overlord_exe.runtime.OverlordBot
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.util.TimeSource
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SimpleProxyBot(
    private val simpleProxyRepository: SimpleProxyRepository,
    private val botMapService: BotMapService,
    private val botComponent: BotComponent,
    private val logger: Logger = LoggerFactory.getLogger(SimpleProxyBot::class.java),
    private val contractService: ContractService,
    private val timeSource: TimeSource,
) : OverlordBot {

    override val descriptor = BotDescriptor(
        internalName = "simple_proxy",
        description = "Simple proxy",
        reviewInterval = null,
    )

    val botContext: BotContext
        get() = botComponent.contextFor(this)

    @PostConstruct
    fun start() {
        logger.info("Starting SimpleProxy")
        val botMap = getBotMap()
        botComponent.startBot(botMap, this)
    }

    fun getBotMap(): BotMap {
        return botMapService.getOrCreateBotMap(descriptor.internalName, descriptor.description)
    }

    fun save(simpleProxy: SimpleProxy): SimpleProxy {
        return simpleProxyRepository.save(simpleProxy)
    }

    fun findByName(name: String): SimpleProxy {
        return simpleProxyRepository.findByName(name)
    }

    fun findByContractSerialNumber(serialNumber: Long): SimpleProxy {
        return simpleProxyRepository.findByContractSerialNumber(serialNumber)
    }

    fun createInitialRecord(keyHolder: BSkyUser, sub: BSkyUser): SimpleProxy {
        val record = SimpleProxy(
            contractSerialNumber = 0,
            bskyUser = sub,
            keyHolderBskyUser = keyHolder,
            state = SimpleProxyState.CREATED,
            createdAt = timeSource.nowInUtc(),
            updatedAt = timeSource.nowInUtc(),
        )
        save(record)
        return record
    }

    fun processIntakeForm(form: SimpleProxyIntakeForm) {
        val record = simpleProxyRepository.findByName(form.name)
        record.isPublic = form.public
        record.state = SimpleProxyState.ISSUED

        val contract = botContext.issueContract(
            shareableToken = record.bskyUser.shareableToken!!,
            terms = "Proxy for ${record.keyHolderBskyUser.handle}. Public: ${record.isPublic}",
            public = form.public,
        )

        record.contractSerialNumber = contract.serialNumber
        record.contractId = contract.id
        save(record)

        botContext.sendDm(
            convoId = record.keyHolderBskyUser.convoId ?: "",
            message = "Contract issued",
        )
        botContext.sendDm(
            convoId = record.bskyUser.convoId ?: "",
            message = "You have been issued a contract by ${record.keyHolderBskyUser.handle}",
        )
    }

    override fun onAccept(ctx: BotContext, contract: Contract) {
        val record = findByContractSerialNumber(contract.serialNumber.toLong())
        if (record.state != SimpleProxyState.ISSUED) {
            return
        }
        record.state = SimpleProxyState.ACCEPTED
        record.contractId = contract.id
        record.acceptedAt = timeSource.nowInUtc()
        save(record)

        ctx.sendDm(
            convoId = record.keyHolderBskyUser.convoId ?: "",
            message = "Contract accepted",
        )
    }

    override fun handleAccept(contract: Contract) {
        onAccept(botContext, contract)
    }

    override fun onRelease(ctx: BotContext, contract: Contract) {
        val record = findByContractSerialNumber(contract.serialNumber.toLong())
        record.state = SimpleProxyState.RELEASED
        save(record)
    }

    override fun handleRelease(contract: Contract) {
        onRelease(botContext, contract)
    }

    private fun findActiveContractsForPair(keyHolder: BSkyUser, sub: BSkyUser): List<Contract> {
        val proxies = simpleProxyRepository.findByBskyUserAndKeyHolderBskyUserAndStateIn(
            sub,
            keyHolder,
            listOf(SimpleProxyState.ACCEPTED),
        )
        if (proxies.isEmpty()) {
            throw IllegalStateException("${keyHolder.handle} does not have any active contract for ${sub.handle}")
        }
        val botName = botContext.externalName
        return proxies.map { p ->
            contractService.getContract(botName, p.contractSerialNumber.toInt())
        }
    }

    fun release(keyHolder: BSkyUser, sub: BSkyUser): Result<Unit> = runCatching {
        findActiveContractsForPair(keyHolder, sub).forEach { contract ->
            botContext.release(contract)
        }
    }

    fun unlock(keyHolder: BSkyUser, sub: BSkyUser): Result<Unit> = runCatching {
        findActiveContractsForPair(keyHolder, sub).forEach { contract ->
            botContext.unlock(contract)
        }
    }

    fun lock(keyHolder: BSkyUser, sub: BSkyUser): Result<Unit> = runCatching {
        findActiveContractsForPair(keyHolder, sub).forEach { contract ->
            botContext.lock(contract)
        }
    }
}
