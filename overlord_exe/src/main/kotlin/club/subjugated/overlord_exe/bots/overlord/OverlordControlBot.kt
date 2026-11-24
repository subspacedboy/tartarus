package club.subjugated.overlord_exe.bots.overlord

import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.convo.ConversationResponse
import club.subjugated.overlord_exe.convo.IntentParam
import club.subjugated.overlord_exe.convo.IntentSpec
import club.subjugated.overlord_exe.convo.ParamType
import club.subjugated.overlord_exe.convo.asBooleanOrNull
import club.subjugated.overlord_exe.convo.asString
import club.subjugated.overlord_exe.convo.intent
import club.subjugated.overlord_exe.convo.stringIntent
import club.subjugated.overlord_exe.convo.unitIntent
import club.subjugated.overlord_exe.machines.SessionEngine
import club.subjugated.overlord_exe.machines.SessionIssueSpec
import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ReleasePolicy
import club.subjugated.overlord_exe.models.UserProfile
import club.subjugated.overlord_exe.runtime.BotContext
import club.subjugated.overlord_exe.runtime.BotDescriptor
import club.subjugated.overlord_exe.runtime.OverlordBot
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.services.EmergencyRelockService
import club.subjugated.overlord_exe.services.TokenResolution
import club.subjugated.overlord_exe.services.UrlService
import club.subjugated.overlord_exe.services.UserProfileService
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.Duration

data class ProxyIntakeParams(
    val otherUser: String,
    val public: Boolean? = null,
)

@Service
class OverlordControlBot(
    private val botMapService: BotMapService,
    private val botComponent: BotComponent,
    private val sessionEngine: SessionEngine,
    private val userProfileService: UserProfileService,
    private val contractService: ContractService,
    private val blueSkyService: BlueSkyService,
    private val urlService: UrlService = UrlService(),
    @Autowired(required = false) private val emergencyRelockService: EmergencyRelockService? = null,
    private val logger: Logger = LoggerFactory.getLogger(OverlordControlBot::class.java),
) : OverlordBot {

    override val descriptor = BotDescriptor(
        internalName = "overlord",
        description = "Overlord Control Bot",
        reviewInterval = Duration.ofMinutes(1),
    )

    val botContext: BotContext
        get() = botComponent.contextFor(this)

    @PostConstruct
    fun start() {
        logger.info("Starting unified OverlordControlBot")
        botComponent.startBot(getBotMap(), this)
    }

    fun getBotMap(): BotMap {
        return botMapService.getOrCreateBotMap(descriptor.internalName, descriptor.description)
    }

    override val commands: Set<String> = setOf(
        "hello",
        "help",
        "timer",
        "superbot",
        "lockme",
        "token",
        "tokens",
        "own",
        "submit",
        "lock",
        "unlock",
        "release",
        "add",
        "remove",
        "relock",
        "contract",
        "accept",
    )

    override val intents: List<IntentSpec<*>> = listOf(
        unitIntent(
            name = "hello",
            description = "The user is only saying hello or greeting you (not asking for help or what you can do).",
            examples = listOf("Hello", "Hey", "Good morning"),
        ) {
            ConversationResponse(text = "Hello 😈! I'm Overlord-exe.")
        },
        unitIntent(
            name = "help",
            description = "The user is asking for help, instructions, commands, or what Overlord-exe can do.",
            examples = listOf("Help", "What can you do?", "How does this work?", "Show me commands"),
        ) {
            ConversationResponse(text = "Here's everything I can do: ${urlService.generateUrl("help")}")
        },
        unitIntent(
            name = "timer",
            description = "Self-lock on a timer. Use ONLY when the sender wants to lock THEMSELVES for a duration. Do NOT use if another @user is mentioned.",
            examples = listOf(
                "I want to self lock.",
                "Lock me for a few hours.",
            ),
        ) { ctx ->
            ConversationResponse(text = startSelfSessionUrl(ctx, listOf("timer")))
        },
        unitIntent(
            name = "superbot",
            description = "Self-lock with Bluesky social tasks (likes/crowd-time) or combined rules. Use ONLY when the sender wants to be locked or owned by Overlord-exe. Do NOT use if another @user is mentioned.",
            examples = listOf(
                "Can you own me?",
                "Lock me for tasks on Bluesky.",
            ),
        ) { ctx ->
            ConversationResponse(text = startSelfSessionUrl(ctx, listOf("bsky_likes", "bsky_crowd_time")))
        },
        intent(
            name = "proxy_contract",
            description = "Create a NEW proxy contract to lock another Bluesky user. Use when the target @user is NOT already listed under 'holding:' in <user_context>.",
            parameters = listOf(
                IntentParam(
                    name = "otherUser",
                    type = ParamType.STRING,
                    description = "Bluesky handle of the other user to put under contract (e.g. @user.bsky.social)",
                ),
                IntentParam(
                    name = "public",
                    type = ParamType.BOOLEAN,
                    description = "Optional: true to announce publicly on Bluesky, false for private.",
                    required = false,
                ),
            ),
            examples = listOf(
                "I want to publicly lock @subspacedboy.subjugated.club",
                "I want to lock @blahblah.bsky.social",
            ),
            parser = { map ->
                ProxyIntakeParams(
                    otherUser = map["otherUser"]!!.asString().trim(),
                    public = map["public"]?.asBooleanOrNull(),
                )
            },
        ) { ctx, params ->
            handleProxyContractIntent(ctx, params)
        },
        stringIntent(
            name = "proxy_unlock",
            paramName = "otherUserHandle",
            paramDescription = "Bluesky handle of the held user to unlock",
            description = "Temporarily unlock a user you are ALREADY holding (must appear under 'holding:' in <user_context>).",
            examples = listOf("I want to unlock @subspacedboy.subjugated.club"),
        ) { ctx, otherUserHandle ->
            executePairAction(ctx, otherUserHandle, "Unlocked") { kh, sub ->
                sessionEngine.unlockPair(botContext, kh.did, sub.did)
            }
        },
        stringIntent(
            name = "proxy_lock",
            paramName = "otherUserHandle",
            paramDescription = "Bluesky handle of the held user to relock",
            description = "Relock a user you are ALREADY holding (must appear under 'holding:' in <user_context>). If they are NOT in 'holding:', use proxy_contract instead.",
            examples = listOf("Relock @subspacedboy.subjugated.club"),
        ) { ctx, otherUserHandle ->
            executePairAction(ctx, otherUserHandle, "Locked") { kh, sub ->
                sessionEngine.lockPair(botContext, kh.did, sub.did)
            }
        },
        stringIntent(
            name = "proxy_release",
            paramName = "otherUserHandle",
            paramDescription = "Bluesky handle of the held user to release",
            description = "Permanently release a user you are ALREADY holding (must appear under 'holding:' in <user_context>).",
            examples = listOf("I want to release @subspacedboy.subjugated.club"),
        ) { ctx, otherUserHandle ->
            executePairAction(ctx, otherUserHandle, "Released") { kh, sub ->
                sessionEngine.releasePair(botContext, kh.did, sub.did)
            }
        },
        stringIntent(
            name = "add_list",
            paramName = "listName",
            paramDescription = "Name of the Bluesky list to join ('Doms', 'Subs', or 'Public Property')",
            description = "The user wants to be added to a Bluesky list.",
            examples = listOf("I want to be added to subs"),
            enumValues = listOf("Doms", "Subs", "Public Property"),
        ) { ctx, listName ->
            val did = ctx.userContext?.did ?: ctx.currentMessage.sender.did
            val atUri = blueSkyService.listNameToUri(listName)
            blueSkyService.addToList(did, atUri)
            ConversationResponse(text = "Added to $listName")
        },
        stringIntent(
            name = "remove_list",
            paramName = "listName",
            paramDescription = "Name of the Bluesky list to leave ('Doms', 'Subs', or 'Public Property')",
            description = "The user wants to be removed from a Bluesky list.",
            examples = listOf("I want to be removed from subs"),
            enumValues = listOf("Doms", "Subs", "Public Property"),
        ) { ctx, listName ->
            val did = ctx.userContext?.did ?: ctx.currentMessage.sender.did
            val atUri = blueSkyService.listNameToUri(listName)
            blueSkyService.removeFromList(did, atUri)
            ConversationResponse(text = "Removed from $listName")
        },
        unitIntent(
            name = "relock",
            description = "The user wants to relock their device after an emergency unlock to resume their active contract(s).",
            examples = listOf("Relock me", "I'm ready to relock", "Put me back in the lock after emergency unlock"),
        ) { ctx ->
            val reply = emergencyRelockService?.relockForUser(ctx) ?: "Relock service unavailable."
            ConversationResponse(text = reply)
        },
    )

    private fun startSelfSessionUrl(ctx: ConversationContext, preselectedMachines: List<String>): String {
        val did = ctx.userContext?.did ?: ctx.currentMessage.sender.did
        val session = sessionEngine.createDraftSession(
            subjectDid = did,
            convoId = ctx.convoId,
            preselectedMachines = preselectedMachines,
        )
        return urlService.generateUrl("session/${session.token}")
    }

    private fun handleProxyContractIntent(ctx: ConversationContext, params: ProxyIntakeParams): ConversationResponse {
        val khProfile = ctx.userProfile ?: userProfileService.findOrCreateByDid(ctx.currentMessage.sender.did)
        val subProfile = userProfileService.findByHandle(params.otherUser)
            ?: return ConversationResponse("User (${params.otherUser}) hasn't signed up... Have them message me.")
        val isPublic = params.public ?: khProfile.preferences.defaultIsPublic ?: false

        return when (val tokenRes = userProfileService.resolveTokenForUser(subProfile.did)) {
            is TokenResolution.None -> {
                ConversationResponse("User (${params.otherUser}) hasn't registered a shareable token yet. Have them message me their token.")
            }
            is TokenResolution.Single -> {
                val session = sessionEngine.createDraftSession(
                    subjectDid = subProfile.did,
                    convoId = subProfile.convoId ?: "",
                    keyholderDid = khProfile.did,
                    preselectedMachines = listOf("proxy_control"),
                    isPublic = isPublic,
                    releasePolicy = ReleasePolicy.MANUAL,
                    explicitShareableToken = tokenRes.token.token,
                )
                sessionEngine.issueSession(
                    ctx = botContext,
                    session = session,
                    spec = SessionIssueSpec(
                        shareableToken = tokenRes.token.token,
                        isPublic = isPublic,
                        releasePolicy = ReleasePolicy.MANUAL,
                        machineTypes = listOf("proxy_control"),
                    ),
                )
                ConversationResponse("Done! Issued proxy contract for ${params.otherUser} using their known token (${tokenRes.token.token}).")
            }
            is TokenResolution.Multiple -> {
                val session = sessionEngine.createDraftSession(
                    subjectDid = subProfile.did,
                    convoId = subProfile.convoId ?: "",
                    keyholderDid = khProfile.did,
                    preselectedMachines = listOf("proxy_control"),
                    isPublic = isPublic,
                    releasePolicy = ReleasePolicy.MANUAL,
                )
                val tokenList = tokenRes.tokens.joinToString(", ") { it.token }
                val url = urlService.generateUrl("session/${session.token}")
                ConversationResponse("${params.otherUser} has multiple shareable tokens on file ($tokenList). Choose which one to use here: $url")
            }
        }
    }

    private fun executePairAction(
        ctx: ConversationContext,
        otherUserHandle: String,
        pastTenseVerb: String,
        action: (UserProfile, UserProfile) -> Result<Unit>,
    ): ConversationResponse {
        val khProfile = ctx.userProfile ?: userProfileService.findOrCreateByDid(ctx.currentMessage.sender.did)
        val subProfile = userProfileService.findByHandle(otherUserHandle)
            ?: return ConversationResponse("User ($otherUserHandle) hasn't signed up... Have them message me.")

        val result = action(khProfile, subProfile)
        return if (result.isSuccess) {
            ConversationResponse("$pastTenseVerb $otherUserHandle")
        } else {
            ConversationResponse(result.exceptionOrNull()?.message ?: "Unknown error")
        }
    }

    override fun handleCommand(ctx: ConversationContext, command: String, args: List<String>): String {
        val senderDid = ctx.userContext?.did ?: ctx.currentMessage.sender.did
        val senderProfile = ctx.userProfile ?: userProfileService.findOrCreateByDid(senderDid)

        return when (command) {
            "hello" -> "Hello 😈"
            "help" -> "Here's everything I can do: ${urlService.generateUrl("help")}"
            "timer" -> startSelfSessionUrl(ctx, listOf("timer"))
            "superbot" -> startSelfSessionUrl(ctx, listOf("bsky_likes", "bsky_crowd_time"))
            "lockme" -> startSelfSessionUrl(ctx, listOf("timer", "bsky_likes"))
            "token" -> {
                val arg = args.firstOrNull() ?: return "Usage: !token <shareableToken>"
                val recorded = userProfileService.recordToken(senderDid, arg, makeDefault = true)
                "Token recorded - ${recorded.token} (${recorded.tokenType.name})"
            }
            "tokens" -> {
                val tokens = userProfileService.getTokensForUser(senderDid)
                if (tokens.isEmpty()) {
                    "No shareable tokens on file yet."
                } else {
                    "Known tokens: " + tokens.joinToString(", ") {
                        if (it.isDefault) "${it.token} (default)" else it.token
                    }
                }
            }
            "own" -> {
                val arg = args.firstOrNull() ?: return "Missing argument"
                val sub = userProfileService.findByHandle(arg)
                    ?: return "User ($arg) hasn't signed up... Have them message me."
                val session = sessionEngine.createDraftSession(
                    subjectDid = sub.did,
                    convoId = sub.convoId ?: "",
                    keyholderDid = senderDid,
                    preselectedMachines = listOf("proxy_control"),
                    releasePolicy = ReleasePolicy.MANUAL,
                )
                urlService.generateUrl("session/${session.token}")
            }
            "submit" -> {
                val arg = args.firstOrNull() ?: return "Missing argument"
                val keyholder = userProfileService.findByHandle(arg)
                    ?: return "User ($arg) hasn't signed up... Have them message me."
                val session = sessionEngine.createDraftSession(
                    subjectDid = senderDid,
                    convoId = ctx.convoId,
                    keyholderDid = keyholder.did,
                    preselectedMachines = listOf("proxy_control"),
                    releasePolicy = ReleasePolicy.MANUAL,
                )
                urlService.generateUrl("session/${session.token}")
            }
            "release" -> {
                val arg = args.firstOrNull() ?: return "Missing argument"
                val sub = userProfileService.findByHandle(arg) ?: return "User hasn't signed up..."
                sessionEngine.releasePair(botContext, senderProfile.did, sub.did)
                "Issuing release"
            }
            "unlock" -> {
                val arg = args.firstOrNull() ?: return "Missing argument"
                val sub = userProfileService.findByHandle(arg) ?: return "User hasn't signed up..."
                sessionEngine.unlockPair(botContext, senderProfile.did, sub.did)
                "Unlocking"
            }
            "lock" -> {
                val arg = args.firstOrNull() ?: return "Missing argument"
                val sub = userProfileService.findByHandle(arg) ?: return "User hasn't signed up..."
                sessionEngine.lockPair(botContext, senderProfile.did, sub.did)
                "Locking"
            }
            "add" -> {
                val list = args.joinToString(" ").lowercase()
                try {
                    val atUri = blueSkyService.listNameToUri(list)
                    blueSkyService.addToList(senderDid, atUri)
                    "Added"
                } catch (_: Exception) {
                    "Unknown list: $list"
                }
            }
            "remove" -> {
                val list = args.joinToString(" ").lowercase()
                try {
                    val atUri = blueSkyService.listNameToUri(list)
                    blueSkyService.removeFromList(senderDid, atUri)
                    "Removed"
                } catch (_: Exception) {
                    "Unknown list: $list"
                }
            }
            "relock" -> emergencyRelockService?.relockForUser(ctx) ?: "Relock service unavailable."
            "contract" -> {
                if (args.size < 3) {
                    return "Usage: !contract <botInternalName> <lockSession> <serialNumber>"
                }
                val botInternalName = args[0]
                val lockSession = args[1]
                val serialNumber = args[2].toIntOrNull()
                    ?: return "Invalid serial number: ${args[2]}"
                try {
                    val botCtx = botComponent.contextFor(botInternalName)
                    botCtx.syncContract(lockSession, serialNumber)
                    "Refreshed"
                } catch (ex: Exception) {
                    logger.warn("Failed to refresh contract $botInternalName ($lockSession/$serialNumber): ${ex.message}")
                    "Failed to refresh contract: ${ex.message ?: "unknown error"}"
                }
            }
            "accept" -> {
                if (args.size < 3) {
                    return "Usage: !accept <botInternalName> <lockSession> <serialNumber>"
                }
                val botInternalName = args[0]
                val lockSession = args[1]
                val serialNumber = args[2].toIntOrNull()
                    ?: return "Invalid serial number: ${args[2]}"
                try {
                    val botMap = botMapService.getBotMap(botInternalName)
                    val contract = contractService.getOrCreateContract(botMap.externalName, lockSession, serialNumber)
                    val handler = botComponent.handlers[botMap]
                        ?: return "No active handler for bot $botInternalName"
                    handler.onAccept(botComponent.contextFor(botInternalName), contract)
                    "Reprocessed accept"
                } catch (ex: Exception) {
                    logger.warn("Failed to reprocess accept $botInternalName ($lockSession/$serialNumber): ${ex.message}")
                    "Failed to reprocess accept: ${ex.message ?: "unknown error"}"
                }
            }
            else -> "Unknown command"
        }
    }

    // --- Coordinator Contract Lifecycle ---

    override fun onAccept(ctx: BotContext, contract: Contract) {
        sessionEngine.onAccept(ctx, contract)
    }

    override fun handleAccept(contract: Contract) {
        onAccept(botContext, contract)
    }

    override fun reviewContracts(ctx: BotContext, contracts: List<Contract>) {
        sessionEngine.reviewContracts(ctx, contracts)
    }

    override fun reviewContracts(contracts: List<Contract>) {
        reviewContracts(botContext, contracts)
    }

    override fun onRelease(ctx: BotContext, contract: Contract) {
        sessionEngine.onRelease(ctx, contract)
    }

    override fun handleRelease(contract: Contract) {
        onRelease(botContext, contract)
    }

    override fun onAbort(ctx: BotContext, contract: Contract) {
        sessionEngine.onAbort(ctx, contract)
    }
}
