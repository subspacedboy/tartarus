package club.subjugated.overlord_exe.bots.simple_proxy

import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.bots.simple_proxy.convo.ProxyContractIntent
import club.subjugated.overlord_exe.bots.simple_proxy.convo.ProxyLockIntent
import club.subjugated.overlord_exe.bots.simple_proxy.convo.ProxyReleaseIntent
import club.subjugated.overlord_exe.bots.simple_proxy.convo.ProxyUnlockIntent
import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.convo.ConversationResponse
import club.subjugated.overlord_exe.models.BSkyUser
import club.subjugated.overlord_exe.models.BotEngagement
import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.EngagementPhase
import club.subjugated.overlord_exe.models.SimpleProxyEngagementData
import club.subjugated.overlord_exe.runtime.BotContext
import club.subjugated.overlord_exe.runtime.BotDescriptor
import club.subjugated.overlord_exe.runtime.OverlordBot
import club.subjugated.overlord_exe.services.BSkyUserService
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.services.Intent
import club.subjugated.overlord_exe.services.UrlService
import club.subjugated.overlord_exe.storage.BotEngagementRepository
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.generateId
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import kotlin.reflect.KClass

data class SimpleProxyIntakeForm(
    var name: String,
    var public: Boolean = false,
)

@Service
class SimpleProxyBot(
    private val engagementRepository: BotEngagementRepository,
    private val botMapService: BotMapService,
    private val botComponent: BotComponent,
    private val contractService: ContractService,
    private val timeSource: TimeSource,
    private val blueSkyService: BlueSkyService? = null,
    private val bSkyUserService: BSkyUserService? = null,
    private val urlService: UrlService = UrlService(),
    private val logger: Logger = LoggerFactory.getLogger(SimpleProxyBot::class.java),
) : OverlordBot {

    override val descriptor = BotDescriptor(
        internalName = "simple_proxy",
        description = "Simple proxy",
        reviewInterval = null,
    )

    override val commands: Set<String> = setOf("token", "own", "submit", "release", "unlock", "lock")

    val botContext: BotContext
        get() = botComponent.contextFor(this)

    @PostConstruct
    fun start() {
        logger.info("Starting SimpleProxy")
        botComponent.startBot(getBotMap(), this)
    }

    fun getBotMap(): BotMap {
        return botMapService.getOrCreateBotMap(descriptor.internalName, descriptor.description)
    }

    // --- BlueSky DM & Intent Handling ---

    override fun handle(convoId: String, message: ConvoDefsMessageView): String {
        val userSvc = bSkyUserService ?: return ""
        val messageMinusBang = message.text.replaceFirst("!", "").trim()
        val chunks = messageMinusBang.split("\\s+".toRegex())

        val bskyUser = userSvc.findOrCreateByDid(message.sender.did)
        val handle = blueSkyService?.resolveDidToHandle(bskyUser.did) ?: bskyUser.handle
        bskyUser.convoId = convoId
        bskyUser.handle = handle
        userSvc.save(bskyUser)

        return when (chunks[0].lowercase()) {
            "token" -> {
                bskyUser.shareableToken = chunks[1]
                userSvc.save(bskyUser)
                "Token recorded - ${bskyUser.shareableToken}"
            }
            "own" -> {
                val subUser = userSvc.findByHandle(chunks[1])
                if (subUser == null) {
                    "User (${chunks[1]}) hasn't signed up... Have them message me."
                } else {
                    val engagement = createInitialRecord(bskyUser, subUser)
                    urlService.generateUrl("simpleproxy/${engagement.name}")
                }
            }
            "submit" -> {
                val otherUser = userSvc.findByHandle(chunks[1])
                if (otherUser == null) {
                    "User (${chunks[1]}) hasn't signed up... Have them message me."
                } else {
                    createInitialRecord(otherUser, bskyUser)
                    "Issuing contract"
                }
            }
            "release" -> {
                val subUser = userSvc.findByHandle(chunks[1])
                if (subUser == null) {
                    "User hasn't signed up..."
                } else {
                    release(bskyUser, subUser)
                    "Issuing release"
                }
            }
            "unlock" -> {
                val subUser = userSvc.findByHandle(chunks[1])
                if (subUser == null) {
                    "User hasn't signed up..."
                } else {
                    unlock(bskyUser, subUser)
                    "Unlocking"
                }
            }
            "lock" -> {
                val subUser = userSvc.findByHandle(chunks[1])
                if (subUser == null) {
                    "User hasn't signed up..."
                } else {
                    lock(bskyUser, subUser)
                    "Locking"
                }
            }
            else -> "Unknown command"
        }
    }

    override fun getIntents(): List<KClass<out Intent>> = listOf(
        ProxyContractIntent::class,
        ProxyUnlockIntent::class,
        ProxyLockIntent::class,
        ProxyReleaseIntent::class,
    )

    override fun handleIntent(ctx: ConversationContext, intent: Intent): ConversationResponse {
        val userSvc = bSkyUserService ?: return ConversationResponse("")
        return when (intent) {
            is ProxyContractIntent -> {
                val subUser = userSvc.findByHandle(intent.proxyIntakeData.otherUser!!)
                if (subUser == null) {
                    ConversationResponse(
                        text = "User (${intent.proxyIntakeData.otherUser}) hasn't signed up... Have them message me.",
                    )
                } else {
                    val engagement = createInitialRecord(ctx.bskyUser!!, subUser)
                    processIntakeForm(
                        SimpleProxyIntakeForm(
                            name = engagement.name,
                            public = intent.proxyIntakeData.public!!,
                        )
                    )
                    ConversationResponse(text = "Done!")
                }
            }
            is ProxyUnlockIntent -> {
                val subUser = userSvc.findByHandle(intent.proxyData.otherUserHandle)
                if (subUser == null) {
                    ConversationResponse(
                        text = "User (${intent.proxyData.otherUserHandle}) hasn't signed up... Have them message me.",
                    )
                } else {
                    val result = unlock(ctx.bskyUser!!, subUser)
                    if (result.isSuccess) {
                        ConversationResponse(text = "Unlocked ${intent.proxyData.otherUserHandle}")
                    } else {
                        ConversationResponse(text = result.exceptionOrNull()?.message ?: "Unknown error")
                    }
                }
            }
            is ProxyLockIntent -> {
                val subUser = userSvc.findByHandle(intent.proxyData.otherUserHandle)
                if (subUser == null) {
                    ConversationResponse(
                        text = "User (${intent.proxyData.otherUserHandle}) hasn't signed up... Have them message me.",
                    )
                } else {
                    val result = lock(ctx.bskyUser!!, subUser)
                    if (result.isSuccess) {
                        ConversationResponse(text = "Locked ${intent.proxyData.otherUserHandle}")
                    } else {
                        ConversationResponse(text = result.exceptionOrNull()?.message ?: "Unknown error")
                    }
                }
            }
            is ProxyReleaseIntent -> {
                val subUser = userSvc.findByHandle(intent.proxyData.otherUserHandle)
                if (subUser == null) {
                    ConversationResponse(
                        text = "User (${intent.proxyData.otherUserHandle}) hasn't signed up... Have them message me.",
                    )
                } else {
                    val result = release(ctx.bskyUser!!, subUser)
                    if (result.isSuccess) {
                        ConversationResponse(text = "Released ${intent.proxyData.otherUserHandle}")
                    } else {
                        ConversationResponse(text = result.exceptionOrNull()?.message ?: "Unknown error")
                    }
                }
            }
            else -> throw IllegalStateException("Unknown intent: $intent")
        }
    }

    // --- Session & Intake Lifecycle ---

    fun save(engagement: BotEngagement): BotEngagement {
        engagement.syncJsonBeforeSave()
        engagement.updatedAt = timeSource.nowInUtc()
        return engagementRepository.save(engagement)
    }

    fun findByName(name: String): BotEngagement {
        return engagementRepository.findByName(name)
            ?: throw IllegalStateException("Unknown SimpleProxy engagement: $name")
    }

    fun findByContractSerialNumber(serialNumber: Int): BotEngagement {
        return engagementRepository.findByBotIdAndContractSerialNumber(descriptor.internalName, serialNumber)
            ?: throw IllegalStateException("Unknown SimpleProxy engagement for serial $serialNumber")
    }

    fun createInitialRecord(keyHolder: BSkyUser, sub: BSkyUser): BotEngagement {
        val engagement = BotEngagement(
            name = generateId("sp-"),
            botId = descriptor.internalName,
            phase = EngagementPhase.CREATED,
            contractSerialNumber = 0,
            shareableToken = sub.shareableToken ?: "",
            subjectDid = sub.did,
            convoId = sub.convoId ?: "",
            keyholderDid = keyHolder.did,
            createdAt = timeSource.nowInUtc(),
            updatedAt = timeSource.nowInUtc(),
            data = SimpleProxyEngagementData(
                keyholderHandle = keyHolder.handle ?: "",
                keyholderConvoId = keyHolder.convoId ?: "",
                subHandle = sub.handle ?: "",
            ),
        )
        return save(engagement)
    }

    fun processIntakeForm(form: SimpleProxyIntakeForm) {
        val engagement = findByName(form.name)
        val data = engagement.data as SimpleProxyEngagementData
        data.isPublic = form.public
        engagement.phase = EngagementPhase.ISSUED

        val shareableToken = engagement.shareableToken?.takeIf { it.isNotBlank() }
            ?: bSkyUserService?.findOrCreateByDid(engagement.subjectDid)?.shareableToken
            ?: throw IllegalStateException("Subject ${engagement.subjectDid} has no shareable token")

        val contract = botContext.issueContract(
            shareableToken = shareableToken,
            terms = "Proxy for ${data.keyholderHandle}. Public: ${data.isPublic}",
            public = form.public,
        )

        engagement.contractSerialNumber = contract.serialNumber
        engagement.contractId = contract.id
        save(engagement)

        val keyholderConvoId = data.keyholderConvoId.ifBlank {
            engagement.keyholderDid?.let { bSkyUserService?.findOrCreateByDid(it)?.convoId } ?: ""
        }
        if (keyholderConvoId.isNotBlank()) {
            botContext.sendDm(keyholderConvoId, "Contract issued")
        }
        if (engagement.convoId.isNotBlank()) {
            botContext.sendDm(
                engagement.convoId,
                "You have been issued a contract by ${data.keyholderHandle}",
            )
        }
    }

    // --- Coordinator Contract Lifecycle ---

    override fun onAccept(ctx: BotContext, contract: Contract) {
        val engagement = findByContractSerialNumber(contract.serialNumber)
        if (engagement.phase != EngagementPhase.ISSUED) {
            return
        }
        val data = engagement.data as SimpleProxyEngagementData
        engagement.phase = EngagementPhase.ACTIVE
        engagement.contractId = contract.id
        engagement.acceptedAt = timeSource.nowInUtc()
        save(engagement)

        val keyholderConvoId = data.keyholderConvoId.ifBlank {
            engagement.keyholderDid?.let { bSkyUserService?.findOrCreateByDid(it)?.convoId } ?: ""
        }
        ctx.sendDm(
            convoId = keyholderConvoId,
            message = "Contract accepted",
        )
    }

    override fun handleAccept(contract: Contract) {
        onAccept(botContext, contract)
    }

    override fun onRelease(ctx: BotContext, contract: Contract) {
        val engagement = findByContractSerialNumber(contract.serialNumber)
        engagement.phase = EngagementPhase.COMPLETE
        save(engagement)
    }

    override fun handleRelease(contract: Contract) {
        onRelease(botContext, contract)
    }

    private fun findActiveContractsForPair(keyHolder: BSkyUser, sub: BSkyUser): List<Contract> {
        val activeEngagements = engagementRepository.findByBotIdAndKeyholderDidAndSubjectDidAndPhaseIn(
            botId = descriptor.internalName,
            keyholderDid = keyHolder.did,
            subjectDid = sub.did,
            phases = listOf(EngagementPhase.ACTIVE),
        )
        if (activeEngagements.isEmpty()) {
            throw IllegalStateException("${keyHolder.handle} does not have any active contract for ${sub.handle}")
        }
        val botName = botContext.externalName
        return activeEngagements.map { eng ->
            contractService.getContract(botName, eng.contractSerialNumber)
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
