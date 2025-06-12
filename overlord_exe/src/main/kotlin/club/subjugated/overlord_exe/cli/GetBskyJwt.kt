package club.subjugated.overlord_exe.cli

import aws.sdk.kotlin.runtime.client.AwsClientOption.Region
import aws.sdk.kotlin.services.bedrock.BedrockClient
import aws.sdk.kotlin.services.bedrock.model.FoundationModelSummary
import aws.sdk.kotlin.services.bedrock.model.ListFoundationModelsRequest
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.converse
import aws.sdk.kotlin.services.bedrockruntime.model.ContentBlock
import aws.sdk.kotlin.services.bedrockruntime.model.ConversationRole
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseRequest
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import aws.sdk.kotlin.services.bedrockruntime.model.Message
import aws.sdk.kotlin.services.bedrockruntime.model.SystemContentBlock
import aws.smithy.kotlin.runtime.content.ByteStream
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.IntentService
import club.subjugated.overlord_exe.services.RealBlueSkyService
import club.subjugated.overlord_exe.util.TimeSource
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
    private val timeSource: TimeSource,
    private val intentService : IntentService

) : Runnable {
    override fun run() {
//        val realBsky = blueSkyService as RealBlueSkyService
//
//        realBsky.getMyLists()

//        intentService.promptTest()
//
//        val json = """{"otherUser": "bob", "public": true}"""
//        val result = intentService.makeTest("proxy_contract", json)
//        print(result)


        val ssbDid = "did:plc:qqk2pgu2uyqnbdp5jldvb3f2"
        val publicPropertyList = "at://did:plc:anwo4rt46p5bauv7fs4jmcga/app.bsky.graph.list/3lr6rygvqhi2h"

//        realBsky.getList(publicPropertyList)
//        realBsky.addToList(ssbDid, publicPropertyList)
//        realBsky.removeFromList(ssbDid, publicPropertyList)
    }
}