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
//        blueSkyService.post()

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