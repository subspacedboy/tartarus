package club.subjugated.overlord_exe.intents

import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.convo.ConversationHandler
import club.subjugated.overlord_exe.convo.ConversationResponse
import club.subjugated.overlord_exe.convo.IntentSpec
import club.subjugated.overlord_exe.convo.stringIntent
import club.subjugated.overlord_exe.convo.unitIntent
import club.subjugated.overlord_exe.services.BSkyUserService
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
import org.springframework.stereotype.Component

@Component
class OverlordConversationHandler(
    private val blueSkyService: BlueSkyService,
    private val bSkyUserService: BSkyUserService,
    private val contractService: ContractService,
    private val botMapService: BotMapService,
    private val botComponent: BotComponent,
) : ConversationHandler {

    override val commands: Set<String> = setOf("hello", "contract", "accept", "add", "remove")

    override val intents: List<IntentSpec<*>> = listOf(
        unitIntent(
            name = "hello",
            description = "The user is saying hello or asking who you are.",
            examples = listOf("Hello", "Who are you?"),
        ) {
            ConversationResponse(text = "Hello 😈! I'm Overlord-exe.")
        },
        stringIntent(
            name = "add_list",
            paramName = "listName",
            paramDescription = "Name of the Bluesky list to join ('Doms', 'Subs', or 'Public Property')",
            description = "The user wants to be added to a Bluesky list. Available lists are: 'Doms', 'Subs', 'Public Property'.",
            examples = listOf("I want to be added to subs"),
        ) { ctx, listName ->
            val did = ctx.bskyUser?.did ?: ctx.currentMessage.sender.did
            val atUri = blueSkyService.listNameToUri(listName)
            blueSkyService.addToList(did, atUri)
            ConversationResponse(text = "Added to $listName")
        },
        stringIntent(
            name = "remove_list",
            paramName = "listName",
            paramDescription = "Name of the Bluesky list to leave ('Doms', 'Subs', or 'Public Property')",
            description = "The user wants to be removed from a Bluesky list. Available lists are: 'Doms', 'Subs', 'Public Property'.",
            examples = listOf("I want to be removed from subs"),
        ) { ctx, listName ->
            val did = ctx.bskyUser?.did ?: ctx.currentMessage.sender.did
            val atUri = blueSkyService.listNameToUri(listName)
            blueSkyService.removeFromList(did, atUri)
            ConversationResponse(text = "Removed from $listName")
        },
    )

    override fun handleCommand(
        ctx: ConversationContext,
        command: String,
        args: List<String>,
    ): String {
        val senderDid = ctx.bskyUser?.did ?: ctx.currentMessage.sender.did
        return when (command) {
            "hello" -> "Hello 😈"
            "contract" -> {
                val botInternalName = args[0]
                val lockSession = args[1]
                val serialNumber = args[2].toInt()

                val botCtx = botComponent.contextFor(botInternalName)
                botCtx.syncContract(lockSession, serialNumber)
                "Refreshed"
            }
            "accept" -> {
                val botInternalName = args[0]
                val lockSession = args[1]
                val serialNumber = args[2].toInt()

                val botMap = botMapService.getBotMap(botInternalName)
                val contract = contractService.getOrCreateContract(botMap.externalName, lockSession, serialNumber)
                val handler = botComponent.handlers[botMap]!!
                handler.onAccept(botComponent.contextFor(botInternalName), contract)
                "Reprocessed accept"
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
            else -> "Unknown command"
        }
    }
}
