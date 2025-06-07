package club.subjugated.overlord_exe.services

import club.subjugated.overlord_exe.storage.BSkyRepostRepository
import club.subjugated.overlord_exe.storage.BskyLikeRepository
import club.subjugated.overlord_exe.util.TimeSource
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import work.socialhub.kbsky.BlueskyFactory
import work.socialhub.kbsky.PLCDirectoryFactory
import work.socialhub.kbsky.api.app.bsky.FeedResource
import work.socialhub.kbsky.api.chat.bsky.ConvoResource
import work.socialhub.kbsky.api.entity.app.bsky.feed.FeedGetAuthorFeedRequest
import work.socialhub.kbsky.api.entity.app.bsky.feed.FeedGetLikesRequest
import work.socialhub.kbsky.api.entity.app.bsky.feed.FeedGetPostThreadRequest
import work.socialhub.kbsky.api.entity.app.bsky.feed.FeedGetRepostedByRequest
import work.socialhub.kbsky.api.entity.app.bsky.feed.FeedPostRequest
import work.socialhub.kbsky.api.entity.chat.bsky.convo.ConvoGetListConvosRequest
import work.socialhub.kbsky.api.entity.chat.bsky.convo.ConvoSendMessageRequest
import work.socialhub.kbsky.api.entity.chat.bsky.convo.ConvoUpdateReadRequest
import work.socialhub.kbsky.api.entity.com.atproto.identity.IdentityResolveHandleRequest
import work.socialhub.kbsky.api.entity.com.atproto.server.ServerCreateSessionRequest
import work.socialhub.kbsky.api.entity.share.AuthRequest
import work.socialhub.kbsky.auth.BearerTokenAuthProvider
import work.socialhub.kbsky.domain.Service.BSKY_SOCIAL
import work.socialhub.kbsky.model.app.bsky.actor.ActorDefsProfileView
import work.socialhub.kbsky.model.app.bsky.feed.FeedDefsPostView
import work.socialhub.kbsky.model.app.bsky.feed.FeedDefsThreadUnion
import work.socialhub.kbsky.model.app.bsky.feed.FeedGetLikesLike
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageInput
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import work.socialhub.kbsky.util.facet.FacetType
import work.socialhub.kbsky.util.facet.FacetUtil
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.time.OffsetDateTime

interface BlueSkyService {
    fun sendDm(convoId: String, message: String)
    fun resolveDidToHandle(did : String) : String
    fun resolveHandleToDid(handle: String) : String
    fun post(message : String) : String
    fun getLikes(uri : String): List<FeedGetLikesLike>
    fun getReposts(uri : String): List<ActorDefsProfileView>
    fun traceThread(uri : String, action:(post : FeedDefsPostView) -> Unit)
    fun getAuthorFeedFromTime(authorDid: String, from: OffsetDateTime, onMessage: (post:  FeedDefsPostView) -> Unit)
    fun getnewDms(onNewMessage: (convoId: String, message :  ConvoDefsMessageView) -> Unit)
}

@Service
class RealBlueSkyService(
    @Value("\${overlord.bsky.user}") private val bskyUser : String,
    @Value("\${overlord.bsky.password_file}") private val bskyPasswordFile : String,
    var publisher: ApplicationEventPublisher,
    var timeSource: TimeSource,
    val bskyLikeRepository: BskyLikeRepository,
    val bSkyRepostRepository: BSkyRepostRepository,
    private val logger: Logger = LoggerFactory.getLogger(BlueSkyService::class.java)
) : BlueSkyService {
    lateinit var accessJwt : BearerTokenAuthProvider
    lateinit var refreshJwt : BearerTokenAuthProvider
    lateinit var lastRefresh : OffsetDateTime
    lateinit var myPds: String
    lateinit var myId : String
    lateinit var chatService:  ConvoResource
    lateinit var feedService: FeedResource

    var dmLogCursor: String? = null

    @PostConstruct
    fun start() {
        val password = Files.readString(Paths.get(bskyPasswordFile)).trim()

        val response = BlueskyFactory
            .instance(BSKY_SOCIAL.uri)
            .server()
            .createSession(
                ServerCreateSessionRequest().also {
                    it.identifier = bskyUser
                    it.password = password
                }
            )
        accessJwt = BearerTokenAuthProvider(response.data.accessJwt)
        refreshJwt = BearerTokenAuthProvider(response.data.refreshJwt)
        lastRefresh = timeSource.nowInUtc()

        myPds = response.data.didDoc!!.asDIDDetails!!.pdsEndpoint()!!
        logger.info("My PDS $myPds")

        myId = response.data.didDoc!!.asDIDDetails!!.id!!

        chatService = BlueskyFactory
            .instance(myPds)
            .convo()

        feedService = BlueskyFactory.instance(BSKY_SOCIAL.uri)
            .feed()
    }

    private fun refreshJwtIfNeeded() {
        if (Duration.between(lastRefresh, timeSource.nowInUtc()) < Duration.ofMinutes(1)) {
            return
        }

        logger.debug("Refreshed session")

        val refreshData = BlueskyFactory
            .instance(BSKY_SOCIAL.uri)
            .server()
            .refreshSession(AuthRequest(
                refreshJwt
            )).data

        accessJwt = BearerTokenAuthProvider(refreshData.accessJwt)
        refreshJwt = BearerTokenAuthProvider(refreshData.refreshJwt)
        lastRefresh = timeSource.nowInUtc()
    }

    override fun getLikes(uri : String): List<FeedGetLikesLike> {
        val likes = feedService.getLikes(FeedGetLikesRequest(accessJwt).also {
            it.uri = uri
        })

        return likes.data.likes
    }

    override fun getReposts(uri : String): List<ActorDefsProfileView> {
        val reposts = feedService.getRepostedBy(FeedGetRepostedByRequest(accessJwt).also {
            it.uri = uri
        })

        return reposts.data.repostedBy
    }

    override fun traceThread(uri : String, action:(post : FeedDefsPostView) -> Unit) {
        refreshJwtIfNeeded()

        val thread = feedService
            .getPostThread(FeedGetPostThreadRequest(accessJwt).also {
                it.uri = uri
            })

        val asViewPost = thread.data.thread.asViewPost
        val post = asViewPost!!.post

        action(post)

        val repliesToProcess = ArrayDeque<FeedDefsThreadUnion>()
        repliesToProcess.addAll(asViewPost.replies!!)

        while(repliesToProcess.isNotEmpty()) {
            val r = repliesToProcess.removeFirst()

            val someResponse = r.asViewPost
            val moreReplies = someResponse!!.replies
            if(moreReplies != null) {
                repliesToProcess.addAll(moreReplies)
            }

            val asPost = someResponse.asViewPost!!.post
            action(asPost)
        }
    }

    override fun getnewDms(onNewMessage: (convoId: String, message :  ConvoDefsMessageView) -> Unit) {
        refreshJwtIfNeeded()

        val convos = chatService.getListConvos(ConvoGetListConvosRequest(accessJwt)).data

        for(c in convos.convos) {
            val lastMessage = c.lastMessage!!.asMessageView
            if(lastMessage!!.sender.did == myId){
                // I was the last person to say something.
            } else {
                onNewMessage(c.id, lastMessage)
                chatService.updateRead(ConvoUpdateReadRequest(accessJwt).also {
                    it.messageId = lastMessage.id
                    it.convoId = c.id
                })
            }
        }
    }

    override fun sendDm(convoId: String, text: String) {
        refreshJwtIfNeeded()

        val message = ConvoDefsMessageInput(
            text = text
        )

        chatService.sendMessage(ConvoSendMessageRequest(accessJwt).also {
            it.convoId = convoId
            it.message = message
        })
    }

    override fun getAuthorFeedFromTime(authorDid: String, from: OffsetDateTime, onMessage: (post:  FeedDefsPostView) -> Unit) {
        refreshJwtIfNeeded()

        var feed = feedService.getAuthorFeed(FeedGetAuthorFeedRequest(accessJwt).also {
            it.actor = authorDid
            it.limit = 10
        }).data

        var finished = false

        outer@ while(!finished) {
            for(f in feed.feed) {
                val createdAt = OffsetDateTime.parse(f.post.record!!.asFeedPost!!.createdAt!!)
                if(createdAt < from) {
                    //Before start time.
                    logger.debug("We're before the start time")
                    finished = true
                    break@outer
                }

                onMessage(f.post)
            }

            feed = feedService.getAuthorFeed(FeedGetAuthorFeedRequest(accessJwt).also {
                it.actor = authorDid
                it.limit = 10
                it.cursor = feed.cursor
            }).data

            if(feed.feed.isEmpty()) {
                logger.debug("No more")
                finished = true
            }
        }
    }

    override fun resolveDidToHandle(did: String) : String {
        val response = PLCDirectoryFactory
            .instance()
            .DIDDetails(did).data

        return response.alsoKnownAs!!.first().replace("at://", "@")
    }

    override fun resolveHandleToDid(handle: String) : String {
        val response = BlueskyFactory
            .instance(BSKY_SOCIAL.uri)
            .identity()
            .resolveHandle(
                IdentityResolveHandleRequest().also {
                    it.handle = handle
                }
            )

        return response.data.did
    }

    override fun post(message : String) : String {
        refreshJwtIfNeeded()

        val list = FacetUtil.extractFacets(message)

        val handles = list.records
            .filter { it.type === FacetType.Mention }
            .map { it.displayText }

        val handleToDidMap = mutableMapOf<String, String>()

        for (handle in handles) {
            val response = BlueskyFactory
                .instance(BSKY_SOCIAL.uri)
                .identity()
                .resolveHandle(
                    IdentityResolveHandleRequest().also {
                        it.handle = handle.substring(1)
                    }
                )
            handleToDidMap[handle] = checkNotNull(response.data.did)
        }

        val facets = list.richTextFacets(handleToDidMap)

        val response = BlueskyFactory
            .instance(BSKY_SOCIAL.uri)
            .feed()
            .post(
                FeedPostRequest(accessJwt).also {
                    it.text = list.displayText()
                    it.facets = facets
                }
            )

        // this will be the at-proto URI.
        return response.data.uri
    }
}