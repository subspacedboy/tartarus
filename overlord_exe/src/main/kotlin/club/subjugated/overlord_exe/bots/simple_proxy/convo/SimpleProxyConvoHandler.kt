package club.subjugated.overlord_exe.bots.simple_proxy.convo

import club.subjugated.overlord_exe.bots.simple_proxy.SimpleProxyService
import club.subjugated.overlord_exe.convo.ConversationHandler
import club.subjugated.overlord_exe.services.BSkyUserService
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.UrlService
import org.springframework.stereotype.Component
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView

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
                val otherUser = bSkyUserService.findByHandle(chunks[1])
                val response = if(otherUser == null) {
                    "User (${chunks[1]}) hasn't signed up... Have them message me."
                } else {
                    // Current user is key holder, second user is sub
                    val record = simpleProxyService.createInitialRecord(bskyUser, otherUser)
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
                val otherUser = bSkyUserService.findByHandle(chunks[1])
                val response = if(otherUser == null) {
                    "User hasn't signed up..."
                } else {
                    simpleProxyService.release(otherUser, bskyUser)
                    "Issuing release"
                }
                return response
            }
            "unlock" -> {
                val otherUser = bSkyUserService.findByHandle(chunks[1])
                val response = if(otherUser == null) {
                    "User hasn't signed up..."
                } else {
                    simpleProxyService.unlock(bskyUser, otherUser)
                    "Unlocking"
                }
                return response
            }
            "lock" -> {
                val otherUser = bSkyUserService.findByHandle(chunks[1])
                val response = if(otherUser == null) {
                    "User hasn't signed up..."
                } else {
                    simpleProxyService.lock(bskyUser, otherUser)
                    "Locking"
                }
                return response
            }
            else -> {
                return "Unknown command"
            }
        }
    }
}