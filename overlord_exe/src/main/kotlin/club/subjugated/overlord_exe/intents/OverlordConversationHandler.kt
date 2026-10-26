package club.subjugated.overlord_exe.intents

import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.convo.ConversationHandler
import club.subjugated.overlord_exe.convo.ConversationResponse
import club.subjugated.overlord_exe.runtime.OverlordBot
import club.subjugated.overlord_exe.services.BSkyUserService
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.services.Intent
import org.springframework.stereotype.Component
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import kotlin.reflect.KClass

@Component
class OverlordConversationHandler(
    private val blueSkyService: BlueSkyService,
    private val bSkyUserService: BSkyUserService,
    private val contractService: ContractService,
    private val botMapService: BotMapService,
    private val botComponent: BotComponent,
) : ConversationHandler {

    override val commands: Set<String> = setOf("hello", "contract", "accept", "add", "remove")

    override fun handle(
        convoId: String,
        message: ConvoDefsMessageView,
    ): String {
        val messageMinusBang = message.text.replaceFirst("!", "").trim()
        val chunks = messageMinusBang.split("\\s+".toRegex())

        val bskyUser = bSkyUserService.findOrCreateByDid(message.sender.did)
        val handle = blueSkyService.resolveDidToHandle(bskyUser.did)
        bskyUser.convoId = convoId
        bskyUser.handle = handle
        bSkyUserService.save(bskyUser)

        return when (chunks[0].lowercase()) {
            "hello" -> "Hello 😈"
            "contract" -> {
                val botInternalName = chunks[1]
                val lockSession = chunks[2]
                val serialNumber = chunks[3].toInt()

                val ctx = botComponent.contextFor(botInternalName)
                ctx.syncContract(lockSession, serialNumber)
                "Refreshed"
            }
            "accept" -> {
                val botInternalName = chunks[1]
                val lockSession = chunks[2]
                val serialNumber = chunks[3].toInt()

                val botMap = botMapService.getBotMap(botInternalName)
                val contract = contractService.getOrCreateContract(botMap.externalName, lockSession, serialNumber)
                val handler = botComponent.handlers[botMap]!!
                if (handler is OverlordBot) {
                    handler.onAccept(botComponent.contextFor(botInternalName), contract)
                } else {
                    handler.handleAccept(contract)
                }
                "Reprocessed accept"
            }
            "add" -> {
                val list = chunks.slice(1 until chunks.size).joinToString(" ").lowercase()
                try {
                    val atUri = blueSkyService.listNameToUri(list)
                    blueSkyService.addToList(message.sender.did, atUri)
                    "Added"
                } catch (_: Exception) {
                    "Unknown list: $list"
                }
            }
            "remove" -> {
                val list = chunks.slice(1 until chunks.size).joinToString(" ").lowercase()
                try {
                    val atUri = blueSkyService.listNameToUri(list)
                    blueSkyService.removeFromList(message.sender.did, atUri)
                    "Removed"
                } catch (_: Exception) {
                    "Unknown list: $list"
                }
            }
            else -> "Unknown command"
        }
    }

    override fun getIntents(): List<KClass<out Intent>> {
        return listOf(
            AddToListIntent::class,
            RemoveFromListIntent::class,
            SayHelloIntent::class,
        )
    }

    override fun handleIntent(
        ctx: ConversationContext,
        intent: Intent,
    ): ConversationResponse {
        return when (intent) {
            is SayHelloIntent -> {
                ConversationResponse(text = "Hello 😈! I'm Overlord-exe.")
            }
            is AddToListIntent -> {
                val atUri = blueSkyService.listNameToUri(intent.addToListData.listName!!)
                blueSkyService.addToList(ctx.bskyUser!!.did, atUri)
                ConversationResponse(text = "Added to ${intent.addToListData.listName}")
            }
            is RemoveFromListIntent -> {
                val atUri = blueSkyService.listNameToUri(intent.removeFromListData.listName!!)
                blueSkyService.removeFromList(ctx.bskyUser!!.did, atUri)
                ConversationResponse(text = "Removed from ${intent.removeFromListData.listName}")
            }
            else -> throw IllegalStateException("Unknown intent")
        }
    }
}
