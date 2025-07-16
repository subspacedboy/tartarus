package club.subjugated.overlord_exe.intents

import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.convo.ConversationHandler
import club.subjugated.overlord_exe.convo.ConversationResponse
import club.subjugated.overlord_exe.services.BSkyUserService
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.services.Intent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
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
    override fun handle(
        convoId: String,
        message: ConvoDefsMessageView
    ): String {
        val messageMinusBang = message.text.replaceFirst("!","").trim()
        val chunks = messageMinusBang.split("\\s+".toRegex())

        val bskyUser = bSkyUserService.findOrCreateByDid(message.sender.did)
        val handle = blueSkyService.resolveDidToHandle(bskyUser.did)
        bskyUser.convoId = convoId
        bskyUser.handle = handle
        bSkyUserService.save(bskyUser)

        val command = chunks[0]

        when(command) {
            "contract" -> {
                val botInternalName = chunks[1]
                val lockSession = chunks[2]
                val serialNumber = chunks[3]

                val botMap = botMapService.getBotMap(botInternalName)

                val scope = CoroutineScope(Dispatchers.Default)
                val exceptionHandler = CoroutineExceptionHandler { _, e ->
                    println("Unhandled exception: $e")
                }
                runBlocking {
                    scope.async(exceptionHandler) {
                        val client = botComponent.botsToClients[botMap]!!
                        val contractInfo = botComponent.requestContract(botMap.externalName, lockSession, serialNumber.toUShort(), client)
                        val contract = contractService.getOrCreateContract(botMap.externalName, lockSession, serialNumber.toInt())
                        contractService.updateContractWithGetContractResponse(contract, contractInfo)
                    }
                }

                return "Refreshed"
            }
            "accept" -> {
                val botInternalName = chunks[1]
                val lockSession = chunks[2]
                val serialNumber = chunks[3]

                val botMap = botMapService.getBotMap(botInternalName)
                val contract = contractService.getOrCreateContract(botMap.externalName, lockSession, serialNumber.toInt())

                val handler = botComponent.handlers[botMap]!!
                handler.handleAccept(contract)
                return "Reprocessed accept"
            }
            else -> {
                return "Unknown command"
            }
        }
    }

    override fun getIntents(): List<KClass<out Intent>> {
        return listOf(AddToListIntent::class,
            RemoveFromListIntent::class,
            SayHelloIntent::class)
    }

    override fun handleIntent(
        ctx: ConversationContext,
        intent: Intent
    ): ConversationResponse {
        val response = when(intent) {
            is SayHelloIntent -> {
                ConversationResponse(
                    text = "Hello 😈! I'm Overlord-exe."
                )
            }
            is AddToListIntent -> {
                val atUri = blueSkyService.listNameToUri(intent.addToListData.listName!!)
                blueSkyService.addToList(ctx.bskyUser!!.did, atUri)
                ConversationResponse(
                    text = "Added to ${intent.addToListData.listName}"
                )
            }
            is RemoveFromListIntent -> {
                val atUri = blueSkyService.listNameToUri(intent.removeFromListData.listName!!)
                blueSkyService.removeFromList(ctx.bskyUser!!.did, atUri)
                ConversationResponse(
                    text = "Removed from ${intent.removeFromListData.listName}"
                )
            }

            else -> {
                throw IllegalStateException("Unknown intent")
            }
        }

        return response
    }
}