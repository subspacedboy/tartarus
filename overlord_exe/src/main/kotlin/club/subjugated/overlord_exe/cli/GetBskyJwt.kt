package club.subjugated.overlord_exe.cli

import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.RealBlueSkyService
import club.subjugated.overlord_exe.util.TimeSource
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import picocli.CommandLine
import work.socialhub.kbsky.util.ATUri
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit

data class AtUriParts(
    val handle: String,
    val collection: String,
    val rkey: String
)

data class LikeRecord(
    val did : String
)

data class RepostRecord(
    val did : String
)

@Component
@CommandLine.Command(name = "get-bsky-jwt", description = ["Get a JWT so we can use the API"])
class GetBskyJwt(
    private val blueSkyService: BlueSkyService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val timeSource: TimeSource

) : Runnable {
    override fun run() {

        val realBsky = blueSkyService as RealBlueSkyService

        realBsky.getMyLists()

        val ssbDid = "did:plc:qqk2pgu2uyqnbdp5jldvb3f2"
        val publicPropertyList = "at://did:plc:anwo4rt46p5bauv7fs4jmcga/app.bsky.graph.list/3lr6rygvqhi2h"

//        realBsky.getList(publicPropertyList)
//        realBsky.addToList(ssbDid, publicPropertyList)
//        realBsky.removeFromList(ssbDid, publicPropertyList)
    }
}