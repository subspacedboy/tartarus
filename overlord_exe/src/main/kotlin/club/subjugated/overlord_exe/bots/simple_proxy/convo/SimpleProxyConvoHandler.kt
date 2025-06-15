package club.subjugated.overlord_exe.bots.simple_proxy.convo

import aws.smithy.kotlin.runtime.util.mapErr
import club.subjugated.overlord_exe.bots.simple_proxy.SimpleProxyService
import club.subjugated.overlord_exe.bots.simple_proxy.web.SimpleProxyIntakeForm
import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.convo.ConversationHandler
import club.subjugated.overlord_exe.convo.ConversationResponse
import club.subjugated.overlord_exe.services.BSkyUserService
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.Intent
import club.subjugated.overlord_exe.services.UrlService
import org.springframework.stereotype.Component
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import kotlin.reflect.KClass

@Component
class SimpleProxyConvoHandler(
    private val simpleProxyService: SimpleProxyService,
    private val blueSkyService: BlueSkyService,
    private val bSkyUserService: BSkyUserService,
    private val urlService: UrlService
) : ConversationHandler {

    override fun handle(
        convoId: String,
        message: ConvoDefsMessageView
    ): String {
        val chunks = message.text.trim().split("\\s+".toRegex())

        val bskyUser = bSkyUserService.findOrCreateByDid(message.sender.did)
        val handle = blueSkyService.resolveDidToHandle(bskyUser.did)
        bskyUser.convoId = convoId
        bskyUser.handle = handle
        bSkyUserService.save(bskyUser)

        val command = chunks[0]

        when(command) {
            "token" -> {
                bskyUser.shareableToken = chunks[1]
                bSkyUserService.save(bskyUser)
                return "Token recorded - ${bskyUser.shareableToken}"
            }
            "own" -> {
                val subUser = bSkyUserService.findByHandle(chunks[1])
                val response = if(subUser == null) {
                    "User (${chunks[1]}) hasn't signed up... Have them message me."
                } else {
                    // Current user is key holder, second user is sub
                    val record = simpleProxyService.createInitialRecord(bskyUser, subUser)
                    val url = urlService.generateUrl("simpleproxy/${record.name}")
                    return url
                }
                return response
            }
            "submit" -> {
                val otherUser = bSkyUserService.findByHandle(chunks[1])
                val response = if(otherUser == null) {
                    "User (${chunks[1]}) hasn't signed up... Have them message me."
                } else {
                    simpleProxyService.createInitialRecord(otherUser, bskyUser)
                    "Issuing contract"
                }
                return response
            }
            "release" -> {
                val subUser = bSkyUserService.findByHandle(chunks[1])
                val response = if(subUser == null) {
                    "User hasn't signed up..."
                } else {
                    simpleProxyService.release(bskyUser, subUser)
                    "Issuing release"
                }
                return response
            }
            "unlock" -> {
                val subUser = bSkyUserService.findByHandle(chunks[1])
                val response = if(subUser == null) {
                    "User hasn't signed up..."
                } else {
                    simpleProxyService.unlock(bskyUser, subUser)
                    "Unlocking"
                }
                return response
            }
            "lock" -> {
                val subUser = bSkyUserService.findByHandle(chunks[1])
                val response = if(subUser == null) {
                    "User hasn't signed up..."
                } else {
                    simpleProxyService.lock(bskyUser, subUser)
                    "Locking"
                }
                return response
            }
            else -> {
                return "Unknown command"
            }
        }
    }

    override fun getIntents(): List<KClass<out Intent>> {
        return listOf(
            ProxyContractIntent::class,
            ProxyUnlockIntent::class,
            ProxyLockIntent::class,
            ProxyReleaseIntent::class
        )
    }

    override fun handleIntent(ctx : ConversationContext, intent: Intent) : ConversationResponse {
        val response = when(intent) {
            is ProxyContractIntent -> {
                val subUser = bSkyUserService.findByHandle(intent.proxyIntakeData.otherUser!!)
                if(subUser == null) {
                    ConversationResponse(
                        text = "User (${intent.proxyIntakeData.otherUser}) hasn't signed up... Have them message me."
                    )
                } else {
                    // Current user is key holder, second user is sub
                    val record = simpleProxyService.createInitialRecord(ctx.bskyUser!!, subUser)
                    val fakeForm = SimpleProxyIntakeForm(
                        name = record.name,
                        public = intent.proxyIntakeData.public!!
                    )
                    simpleProxyService.processIntakeForm(fakeForm)
                    ConversationResponse(
                        text = "Done!"
                    )
                }
            }
            is ProxyUnlockIntent -> {
                val subUser = bSkyUserService.findByHandle(intent.proxyData.otherUserHandle)
                if(subUser == null) {
                    ConversationResponse(
                        text = "User (${intent.proxyData.otherUserHandle}) hasn't signed up... Have them message me."
                    )
                } else {
                    val result = simpleProxyService.unlock(ctx.bskyUser!!, subUser)
                    if(result.isSuccess) {
                        ConversationResponse(
                            text = "Unlocked ${intent.proxyData.otherUserHandle}"
                        )
                    } else {
                        ConversationResponse(
                            text = result.exceptionOrNull()?.message ?: "Unknown error"
                        )
                    }
                }
            }
            is ProxyLockIntent -> {
                val subUser = bSkyUserService.findByHandle(intent.proxyData.otherUserHandle)
                if(subUser == null) {
                    ConversationResponse(
                        text = "User (${intent.proxyData.otherUserHandle}) hasn't signed up... Have them message me."
                    )
                } else {
                    val result = simpleProxyService.lock(ctx.bskyUser!!, subUser)
                    if(result.isSuccess) {
                        ConversationResponse(
                            text = "Locked ${intent.proxyData.otherUserHandle}"
                        )
                    } else {
                        ConversationResponse(
                            text = result.exceptionOrNull()?.message ?: "Unknown error"
                        )
                    }
                }
            }
            is ProxyReleaseIntent -> {
                val subUser = bSkyUserService.findByHandle(intent.proxyData.otherUserHandle)
                if(subUser == null) {
                    ConversationResponse(
                        text = "User (${intent.proxyData.otherUserHandle}) hasn't signed up... Have them message me."
                    )
                } else {
                    val result = simpleProxyService.release(ctx.bskyUser!!, subUser)
                    if(result.isSuccess) {
                        ConversationResponse(
                            text = "Released ${intent.proxyData.otherUserHandle}"
                        )
                    } else {
                        ConversationResponse(
                            text = result.exceptionOrNull()?.message ?: "Unknown error"
                        )
                    }
                }
            }
            else -> {
                throw IllegalStateException("Unknown type")
            }
        }
        return response
    }
}