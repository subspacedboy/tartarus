package club.subjugated.overlord_exe.runtime

import club.subjugated.overlord_exe.bots.general.MessageHandler
import club.subjugated.overlord_exe.models.Contract
import java.time.Duration

/**
 * Declarative metadata describing an independent logical bot hosted inside the Overlord umbrella runtime.
 */
data class BotDescriptor(
    val internalName: String,
    val description: String,
    val reviewInterval: Duration? = null,
)

/**
 * Permission grant for a secondary co-bot included on a Contract (for example, the "announcer" observer bot).
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
 * Primary SPI for a logical bot hosted within the Overlord umbrella runtime.
 *
 * Extends [MessageHandler] with default bridge methods so existing callers and tests can invoke either
 * the context-aware hooks (`onAccept(ctx, contract)`) or the legacy callbacks (`handleAccept(contract)`).
 */
interface OverlordBot : MessageHandler {
    val descriptor: BotDescriptor

    fun onStart(ctx: BotContext) {}

    fun reviewContracts(ctx: BotContext, contracts: List<Contract>) {
        reviewContracts(contracts)
    }

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

    override fun reviewContracts(contracts: List<Contract>) {}
    override fun handleAccept(contract: Contract) {}
    override fun handleRelease(contract: Contract) {}
    override fun handleLock(contract: Contract) {}
    override fun handleUnlock(contract: Contract) {}
    override fun handleEmergencyUnlock(contract: Contract) {}
}
