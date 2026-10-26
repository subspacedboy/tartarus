package club.subjugated.overlord_exe.runtime

import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.convo.ConversationHandler
import club.subjugated.overlord_exe.convo.ConversationResponse
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.services.Intent
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import java.time.Duration
import kotlin.reflect.KClass

/**
 * Metadata describing a logical bot hosted within the Overlord umbrella runtime.
 */
data class BotDescriptor(
    val internalName: String,
    val description: String,
    val reviewInterval: Duration? = Duration.ofMinutes(1),
)

/**
 * Co-bot permission grant included on a contract at issuance time.
 */
data class CoBotGrant(
    val botName: String,
    val compressedPublicKey: ByteArray,
    val receiveEvents: Boolean = true,
    val canUnlock: Boolean = false,
    val canLock: Boolean = false,
    val canRelease: Boolean = false,
)

/**
 * Unified SPI for logical bots hosted inside the Overlord runtime.
 *
 * Combines Coordinator contract lifecycle hooks ([onAccept], [onRelease], [onAbort], [reviewContracts])
 * with BlueSky DM command and LLM intent handling ([ConversationHandler]).
 */
interface OverlordBot : ConversationHandler {
    val descriptor: BotDescriptor

    // --- BlueSky DM & Intent defaults (override in bots that accept DM commands/intents) ---
    override val commands: Set<String>
        get() = emptySet()

    override fun handle(convoId: String, message: ConvoDefsMessageView): String = ""

    override fun getIntents(): List<KClass<out Intent>> = emptyList()

    override fun handleIntent(ctx: ConversationContext, intent: Intent): ConversationResponse =
        ConversationResponse("")

    // --- Coordinator Contract lifecycle hooks ---
    fun onStart(ctx: BotContext) {}

    fun onAccept(ctx: BotContext, contract: Contract) {
        handleAccept(contract)
    }

    fun onRelease(ctx: BotContext, contract: Contract) {
        handleRelease(contract)
    }

    fun onLock(ctx: BotContext, contract: Contract) {
        handleLock(contract)
    }

    fun onUnlock(ctx: BotContext, contract: Contract) {
        handleUnlock(contract)
    }

    fun onEmergencyUnlock(ctx: BotContext, contract: Contract) {
        handleEmergencyUnlock(contract)
    }

    fun onAbort(ctx: BotContext, contract: Contract) {}

    fun reviewContracts(ctx: BotContext, contracts: List<Contract>) {
        reviewContracts(contracts)
    }

    // Convenience overloads for direct unit/integration test calls
    fun handleAccept(contract: Contract) {}
    fun handleRelease(contract: Contract) {}
    fun handleLock(contract: Contract) {}
    fun handleUnlock(contract: Contract) {}
    fun handleEmergencyUnlock(contract: Contract) {}
    fun reviewContracts(contracts: List<Contract>) {}
}
