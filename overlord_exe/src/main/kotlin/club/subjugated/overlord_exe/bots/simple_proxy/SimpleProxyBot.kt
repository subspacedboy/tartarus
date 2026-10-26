package club.subjugated.overlord_exe.bots.simple_proxy

import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.convo.ConversationResponse
import club.subjugated.overlord_exe.convo.IntentParam
import club.subjugated.overlord_exe.convo.IntentSpec
import club.subjugated.overlord_exe.convo.ParamType
import club.subjugated.overlord_exe.convo.intent
import club.subjugated.overlord_exe.convo.stringIntent
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
import club.subjugated.overlord_exe.services.UrlService
import club.subjugated.overlord_exe.storage.BotEngagementRepository
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.generateId
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

data class SimpleProxyIntakeForm(
    var name: String,
    var public: Boolean = false,
)

data class ProxyIntakeParams(
    val otherUser: String,
    val public: Boolean,
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

    override val intents: List<IntentSpec<*>> = listOf(
        intent(
            name = "proxy_contract",
            description = "The user is trying to own, lock, or put another user under a new contract.",
            parameters = listOf(
                IntentParam(
                    name = "otherUser",
                    type = ParamType.STRING,
                    description = "Bluesky handle of the other user to put under contract (e.g. @user.bsky.social)",
                ),
                IntentParam(
                    name = "public",
                    type = ParamType.BOOLEAN,
                    description = "Whether the contract events should be public (true or false)",
                ),
            ),
            examples = listOf(
                "I want to publicly lock @subspacedboy.subjugated.club",
            ),
            parser = { map ->
                ProxyIntakeParams(
                    otherUser = map["otherUser"]!!.asString().trim(),
                    public = map["public"]!!.asBoolean(),
                )
            },
        ) { ctx, params ->
            val userSvc = bSkyUserService ?: return@intent ConversationResponse("")
            val subUser = userSvc.findByHandle(params.otherUser)
            if (subUser == null) {
                ConversationResponse(
                    text = "User (${params.otherUser}) hasn't signed up... Have them message me.",
                )
            } else {
                val engagement = createInitialRecord(ctx.bskyUser!!, subUser)
                processIntakeForm(
                    SimpleProxyIntakeForm(
                        name = engagement.name,
                        public = params.public,
                    )
                )
                ConversationResponse(text = "Done!")
            }
        },
        stringIntent(
            name = "proxy_unlock",
            paramName = "otherUserHandle",
            paramDescription = "Bluesky handle of the user to unlock",
            description = "The user is trying to unlock another user.",
            examples = listOf("I want to unlock @subspacedboy.subjugated.club"),
        ) { ctx, otherUserHandle ->
            executePairAction(ctx, otherUserHandle, "Unlocked", ::unlock)
        },
        stringIntent(
            name = "proxy_lock",
            paramName = "otherUserHandle",
            paramDescription = "Bluesky handle of the user to relock",
            description = "The user is trying to relock another user that is already under contract. If you are not sure whether it is a new contract, ask first.",
            examples = listOf("Lock @subspacedboy.subjugated.club"),
        ) { ctx, otherUserHandle ->
            executePairAction(ctx, otherUserHandle, "Locked", ::lock)
        },
        stringIntent(
            name = "proxy_release",
            paramName = "otherUserHandle",
            paramDescription = "Bluesky handle of the user to release from contract",
            description = "The user is trying to release another user from contract.",
            examples = listOf("I want to release @subspacedboy.subjugated.club"),
        ) { ctx, otherUserHandle ->
            executePairAction(ctx, otherUserHandle, "Released", ::release)
        },
    )

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

    private fun executePairAction(
        ctx: ConversationContext,
        otherUserHandle: String,
        pastTenseVerb: String,
        action: (BSkyUser, BSkyUser) -> Result<Unit>,
    ): ConversationResponse {
        val userSvc = bSkyUserService ?: return ConversationResponse("")
        val subUser = userSvc.findByHandle(otherUserHandle)
        return if (subUser == null) {
            ConversationResponse(
                text = "User ($otherUserHandle) hasn't signed up... Have them message me.",
            )
        } else {
            val result = action(ctx.bskyUser!!, subUser)
            if (result.isSuccess) {
                ConversationResponse(text = "$pastTenseVerb $otherUserHandle")
            } else {
                ConversationResponse(text = result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    override fun handleCommand(ctx: ConversationContext, command: String, args: List<String>): String {
        val userSvc = bSkyUserService ?: return ""
        val bskyUser = ctx.bskyUser ?: userSvc.findOrCreateByDid(ctx.currentMessage.sender.did).also { u ->
            u.convoId = ctx.convoId
            u.handle = blueSkyService?.resolveDidToHandle(u.did) ?: u.handle
            userSvc.save(u)
        }
        val arg = args.firstOrNull() ?: return "Missing argument"

        return when (command) {
            "token" -> {
                bskyUser.shareableToken = arg
                userSvc.save(bskyUser)
                "Token recorded - ${bskyUser.shareableToken}"
            }
            "own" -> {
                val subUser = userSvc.findByHandle(arg)
                if (subUser == null) {
                    "User ($arg) hasn't signed up... Have them message me."
                } else {
                    val engagement = createInitialRecord(bskyUser, subUser)
                    urlService.generateUrl("simpleproxy/${engagement.name}")
                }
            }
            "submit" -> {
                val otherUser = userSvc.findByHandle(arg)
                if (otherUser == null) {
                    "User ($arg) hasn't signed up... Have them message me."
                } else {
                    createInitialRecord(otherUser, bskyUser)
                    "Issuing contract"
                }
            }
            "release" -> {
                val subUser = userSvc.findByHandle(arg)
                if (subUser == null) {
                    "User hasn't signed up..."
                } else {
                    release(bskyUser, subUser)
                    "Issuing release"
                }
            }
            "unlock" -> {
                val subUser = userSvc.findByHandle(arg)
                if (subUser == null) {
                    "User hasn't signed up..."
                } else {
                    unlock(bskyUser, subUser)
                    "Unlocking"
                }
            }
            "lock" -> {
                val subUser = userSvc.findByHandle(arg)
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
