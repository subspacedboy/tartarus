package club.subjugated.overlord_exe.runtime

import club.subjugated.fb.event.PeriodicUpdate
import club.subjugated.fb.event.StartedUpdate
import club.subjugated.overlord_exe.convo.ConversationHandler
import club.subjugated.overlord_exe.models.Contract
import java.time.Duration

/**
 * Metadata describing an independent bot managed under the Overlord umbrella runtime.
 *
 * @property internalName Stable internal identifier used for deterministic HKDF key derivation and database lookup.
 * @property description Human-readable description sent during coordinator registration.
 * @property reviewInterval Optional interval for periodic contract review. If `null`, this bot does not run periodic reviews.
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
 * Unified interface for a bot hosted inside the Overlord umbrella runtime.
 *
 * Every [OverlordBot] is also a [ConversationHandler] (with no-op defaults for bots like AnnouncerBot
 * that do not handle BlueSky DMs or intents).
 */
interface OverlordBot : ConversationHandler {
    val descriptor: BotDescriptor

    // --- Coordinator Contract Lifecycle ---

    fun onStart(ctx: BotContext) {}

    fun reviewContracts(ctx: BotContext, contracts: List<Contract>) {
        reviewContracts(contracts)
    }

    fun reviewContracts(contracts: List<Contract>) {}

    fun onAccept(ctx: BotContext, contract: Contract) {
        handleAccept(contract)
    }

    fun handleAccept(contract: Contract) {}

    fun onRelease(ctx: BotContext, contract: Contract) {
        handleRelease(contract)
    }

    fun handleRelease(contract: Contract) {}

    fun onLock(ctx: BotContext, contract: Contract) {
        handleLock(contract)
    }

    fun handleLock(contract: Contract) {}

    fun onUnlock(ctx: BotContext, contract: Contract) {
        handleUnlock(contract)
    }

    fun handleUnlock(contract: Contract) {}

    fun onEmergencyUnlock(ctx: BotContext, contract: Contract) {
        handleEmergencyUnlock(contract)
    }

    fun handleEmergencyUnlock(contract: Contract) {}

    fun onStartedUpdate(ctx: BotContext, contract: Contract, update: StartedUpdate) {
        handleStartedUpdate(contract, update)
    }

    fun handleStartedUpdate(contract: Contract, update: StartedUpdate) {}

    fun onPeriodicUpdate(ctx: BotContext, contract: Contract, update: PeriodicUpdate) {
        handlePeriodicUpdate(contract, update)
    }

    fun handlePeriodicUpdate(contract: Contract, update: PeriodicUpdate) {}

    fun onAbort(ctx: BotContext, contract: Contract) {}
}
