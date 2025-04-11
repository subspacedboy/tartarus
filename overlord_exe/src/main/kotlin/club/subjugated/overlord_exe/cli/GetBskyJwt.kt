package club.subjugated.overlord_exe.cli

import club.subjugated.overlord_exe.bots.timer_bot.events.IssueContract
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.util.TimeSource
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import picocli.CommandLine

@Component
@CommandLine.Command(name = "get-bsky-jwt", description = ["Get a JWT so we can use the API"])
class GetBskyJwt(
    private val blueSkyService: BlueSkyService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val timeSource: TimeSource
) : Runnable {
    override fun run() {
        val uri = "at://did:plc:6eneuye2a52t5hbbcaxo7n7h/app.bsky.feed.post/3lmfnuvnwk22i"
        var totalLikes = 0

        var originalAuthor : String? = null
        blueSkyService.traceThread(uri) { post ->
            if(originalAuthor.isNullOrBlank()) {
                originalAuthor = post.author!!.did
                println("Original author: ${post.author!!.handle}")
            }

            if(post.author!!.did == originalAuthor) {
                println("Processing ${post.author!!.handle} message")
                totalLikes += post.likeCount!!
            }
        }

        println("Total likes accumulated: ${totalLikes}")

//        blueSkyService.getnewDms {convoId, message ->
//            val shareableToken = message.text
//            val authorDid = message.sender.did
//
//            val before = timeSource.nowInUtc().minusDays(2)
//            blueSkyService.getAuthorFeedFromTime(authorDid, before) { message ->
//                println(message)
//            }
//
//            applicationEventPublisher.publishEvent(IssueContract(
//                    source = this,
//                    shareableToken = shareableToken
//                ))
//            blueSkyService.sendDm(convoId, "Issued")
//        }



//        val stream = ATProtocolStreamFactory
//            .instance(
//            )
//            .sync()
//            .subscribeRepos(
//                SyncSubscribeReposRequest().also {
//                    it.filter = listOf(
//                        "app.bsky.feed.post"
//                    )
//                }
//            )

//        stream.eventCallback(
//            object : EventCallback {
//                override fun onEvent(
//                    cid: String?,
//                    uri: String?,
//                    record: RecordUnion
//                ) {
//                    print(record)
//                }
//            })
    }
}