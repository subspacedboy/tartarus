package club.subjugated.overlord_exe.cli

import club.subjugated.overlord_exe.services.BlueSkyService
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

fun parseBlueskyUri(input: String): AtUriParts? {
    val noProto = input.replace("https://", "")
    val pieces = noProto.split("/")

    val handle = pieces[2]
    val collection = pieces[3]
    val rkey = pieces[4]

    val atprotoCollection = when (collection) {
        "post" -> "app.bsky.feed.post"
        else -> throw IllegalStateException("Unhandled atProto collection")
    }

    return AtUriParts(handle, atprotoCollection, rkey)
}

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
//        val parts = parseBlueskyUri("https://bsky.app/profile/subspacedboy.subjugated.club/post/3lpzastr63s2d")
//
//        val startTime = timeSource.nowInUtc()
//
//        val did = blueSkyService.resolveHandleToDid(parts!!.handle)
//        val atUri = ATUri(did, parts.collection, parts.rkey)
//        val uri = "at://${atUri.did}/${atUri.recordType}/${atUri.rkey}"
//
//        val totalLikes = HashSet<LikeRecord>()
//        val totalReposts = HashSet<RepostRecord>()
//
//        blueSkyService.traceThread(uri) {
//            post ->
//            val likes = blueSkyService.getLikes(post.uri!!)
//            likes.forEach {
//                totalLikes.add(LikeRecord(it.actor.did))
//            }
//
//            val reposts = blueSkyService.getReposts(post.uri!!)
//            reposts.forEach {
//                totalReposts.add(RepostRecord(it.did))
//            }
//
//        }
//
//        val addMinutes = Duration.of(totalLikes.size.toLong(), ChronoUnit.MINUTES)
//        val addHours = Duration.of(totalReposts.size.toLong(), ChronoUnit.HOURS)
//
//        val newTime = startTime.plus(addMinutes).plus(addHours)
    }
}