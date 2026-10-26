package club.subjugated.overlord_exe.bots.simple_proxy.convo

import club.subjugated.overlord_exe.bots.simple_proxy.SimpleProxyBot
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
    private val simpleProxyBot: SimpleProxyBot,
    private val blueSkyService: BlueSkyService,
    private val bSkyUserService: BSkyUserService,
    private val urlService: UrlService,
) : ConversationHandler {

    override val commands: Set<String> = setOf("token", "own", "submit", "release", "unlock", "lock")

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

        val command = chunks[0].lowercase()

        return when (command) {
            "token" -> {
                bskyUser.shareableToken = chunks[1]
                bSkyUserService.save(bskyUser)
                "Token recorded - ${bskyUser.shareableToken}"
            }
            "own" -> {
                val subUser = bSkyUserService.findByHandle(chunks[1])
                if (subUser == null) {
                    "User (${chunks[1]}) hasn't signed up... Have them message me."
                } else {
                    val record = simpleProxyBot.createInitialRecord(bskyUser, subUser)
                    urlService.generateUrl("simpleproxy/${record.name}")
                }
            }
            "submit" -> {
                val otherUser = bSkyUserService.findByHandle(chunks[1])
                if (otherUser == null) {
                    "User (${chunks[1]}) hasn't signed up... Have them message me."
                } else {
                    simpleProxyBot.createInitialRecord(otherUser, bskyUser)
                    "Issuing contract"
                }
            }
            "release" -> {
                val subUser = bSkyUserService.findByHandle(chunks[1])
                if (subUser == null) {
                    "User hasn't signed up..."
                } else {
                    simpleProxyBot.release(bskyUser, subUser)
                    "Issuing release"
                }
            }
            "unlock" -> {
                val subUser = bSkyUserService.findByHandle(chunks[1])
                if (subUser == null) {
                    "User hasn't signed up..."
                } else {
                    simpleProxyBot.unlock(bskyUser, subUser)
                    "Unlocking"
                }
            }
            "lock" -> {
                val subUser = bSkyUserService.findByHandle(chunks[1])
                if (subUser == null) {
                    "User hasn't signed up..."
                } else {
                    simpleProxyBot.lock(bskyUser, subUser)
                    "Locking"
                }
            }
            else -> "Unknown command"
        }
    }

    override fun getIntents(): List<KClass<out Intent>> {
        return listOf(
            ProxyContractIntent::class,
            ProxyUnlockIntent::class,
            ProxyLockIntent::class,
            ProxyReleaseIntent::class,
        )
    }

    override fun handleIntent(ctx: ConversationContext, intent: Intent): ConversationResponse {
        return when (intent) {
            is ProxyContractIntent -> {
                val subUser = bSkyUserService.findByHandle(intent.proxyIntakeData.otherUser!!)
                if (subUser == null) {
                    ConversationResponse(
                        text = "User (${intent.proxyIntakeData.otherUser}) hasn't signed up... Have them message me.",
                    )
                } else {
                    val record = simpleProxyBot.createInitialRecord(ctx.bskyUser!!, subUser)
                    val fakeForm = SimpleProxyIntakeForm(
                        name = record.name,
                        public = intent.proxyIntakeData.public!!,
                    )
                    simpleProxyBot.processIntakeForm(fakeForm)
                    ConversationResponse(text = "Done!")
                }
            }
            is ProxyUnlockIntent -> {
                val subUser = bSkyUserService.findByHandle(intent.proxyData.otherUserHandle)
                if (subUser == null) {
                    ConversationResponse(
                        text = "User (${intent.proxyData.otherUserHandle}) hasn't signed up... Have them message me.",
                    )
                } else {
                    val result = simpleProxyBot.unlock(ctx.bskyUser!!, subUser)
                    if (result.isSuccess) {
                        ConversationResponse(text = "Unlocked ${intent.proxyData.otherUserHandle}")
                    } else {
                        ConversationResponse(text = result.exceptionOrNull()?.message ?: "Unknown error")
                    }
                }
            }
            is ProxyLockIntent -> {
                val subUser = bSkyUserService.findByHandle(intent.proxyData.otherUserHandle)
                if (subUser == null) {
                    ConversationResponse(
                        text = "User (${intent.proxyData.otherUserHandle}) hasn't signed up... Have them message me.",
                    )
                } else {
                    val result = simpleProxyBot.lock(ctx.bskyUser!!, subUser)
                    if (result.isSuccess) {
                        ConversationResponse(text = "Locked ${intent.proxyData.otherUserHandle}")
                    } else {
                        ConversationResponse(text = result.exceptionOrNull()?.message ?: "Unknown error")
                    }
                }
            }
            is ProxyReleaseIntent -> {
                val subUser = bSkyUserService.findByHandle(intent.proxyData.otherUserHandle)
                if (subUser == null) {
                    ConversationResponse(
                        text = "User (${intent.proxyData.otherUserHandle}) hasn't signed up... Have them message me.",
                    )
                } else {
                    val result = simpleProxyBot.release(ctx.bskyUser!!, subUser)
                    if (result.isSuccess) {
                        ConversationResponse(text = "Released ${intent.proxyData.otherUserHandle}")
                    } else {
                        ConversationResponse(text = result.exceptionOrNull()?.message ?: "Unknown error")
                    }
                }
            }
            else -> throw IllegalStateException("Unknown type")
        }
    }
}
