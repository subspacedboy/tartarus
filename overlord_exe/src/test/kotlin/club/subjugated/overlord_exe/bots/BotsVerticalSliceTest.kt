package club.subjugated.overlord_exe.bots

import club.subjugated.overlord_exe.bots.announcer.AnnouncerBot
import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.bots.overlord.OverlordControlBot
import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.machines.BSkyLikesMachine
import club.subjugated.overlord_exe.machines.ProxyControlMachine
import club.subjugated.overlord_exe.machines.SessionEngine
import club.subjugated.overlord_exe.machines.SessionIssueSpec
import club.subjugated.overlord_exe.machines.TimerConfig
import club.subjugated.overlord_exe.machines.TimerMachine
import club.subjugated.overlord_exe.models.BSkyLikesMachineState
import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractSession
import club.subjugated.overlord_exe.models.ContractState
import club.subjugated.overlord_exe.models.MachineStatus
import club.subjugated.overlord_exe.models.ReleasePolicy
import club.subjugated.overlord_exe.models.SessionMachine
import club.subjugated.overlord_exe.models.SessionPhase
import club.subjugated.overlord_exe.models.UserContext
import club.subjugated.overlord_exe.models.UserProfile
import club.subjugated.overlord_exe.models.UserShareableToken
import club.subjugated.overlord_exe.runtime.BotContext
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.services.TokenResolution
import club.subjugated.overlord_exe.services.UrlService
import club.subjugated.overlord_exe.services.UserContextService
import club.subjugated.overlord_exe.services.UserProfileService
import club.subjugated.overlord_exe.storage.ContractRepository
import club.subjugated.overlord_exe.storage.ContractSessionRepository
import club.subjugated.overlord_exe.storage.SessionMachineRepository
import club.subjugated.overlord_exe.storage.UserProfileRepository
import club.subjugated.overlord_exe.storage.UserShareableTokenRepository
import club.subjugated.overlord_exe.util.ShareableTokenType
import club.subjugated.overlord_exe.util.TimeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageViewSender
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong

class BotsVerticalSliceTest {

    private var now = OffsetDateTime.of(2026, 11, 24, 12, 0, 0, 0, ZoneOffset.UTC)
    private val timeSource = object : TimeSource() {
        override fun nowInUtc(): OffsetDateTime = now
    }

    private val idGen = AtomicLong(1)
    private val sessions = mutableListOf<ContractSession>()
    private val machines = mutableListOf<SessionMachine>()
    private val profiles = mutableMapOf<String, UserProfile>()
    private val tokens = mutableListOf<UserShareableToken>()

    private lateinit var sessionRepo: ContractSessionRepository
    private lateinit var machineRepo: SessionMachineRepository
    private lateinit var profileRepo: UserProfileRepository
    private lateinit var tokenRepo: UserShareableTokenRepository
    private lateinit var contractRepo: ContractRepository
    private lateinit var blueSkyService: BlueSkyService
    private lateinit var botContext: BotContext
    private lateinit var userProfileService: UserProfileService
    private lateinit var sessionEngine: SessionEngine
    private lateinit var overlordBot: OverlordControlBot

    @BeforeEach
    fun setUp() {
        sessions.clear()
        machines.clear()
        profiles.clear()
        tokens.clear()

        sessionRepo = mock {
            on { save(any<ContractSession>()) } doAnswer { inv ->
                val s = inv.getArgument<ContractSession>(0)
                if (s.id == 0L) {
                    s.id = idGen.getAndIncrement()
                    sessions.add(s)
                }
                s
            }
            on { findByToken(any()) } doAnswer { inv ->
                sessions.find { it.token == inv.getArgument<String>(0) }
            }
            on { findByContractId(any()) } doAnswer { inv ->
                sessions.find { it.contractId == inv.getArgument<Long>(0) }
            }
            on { findByBotIdAndContractSerialNumber(any(), any()) } doAnswer { inv ->
                val botId = inv.getArgument<String>(0)
                val serial = inv.getArgument<Int>(1)
                sessions.find { it.botId == botId && it.contractSerialNumber == serial }
            }
            on { findBySubjectDidAndPhaseIn(any(), any()) } doAnswer { inv ->
                val did = inv.getArgument<String>(0)
                @Suppress("UNCHECKED_CAST")
                val phases = inv.getArgument<Collection<SessionPhase>>(1)
                sessions.filter { it.subjectDid == did && it.phase in phases }
            }
        }

        machineRepo = mock {
            on { save(any<SessionMachine>()) } doAnswer { inv ->
                val m = inv.getArgument<SessionMachine>(0)
                if (m.id == 0L) {
                    m.id = idGen.getAndIncrement()
                    machines.add(m)
                }
                m
            }
            on { findBySessionIdOrderByIdAsc(any()) } doAnswer { inv ->
                machines.filter { it.sessionId == inv.getArgument<Long>(0) }
            }
            on { findByInfoToken(any()) } doAnswer { inv ->
                machines.find { it.infoToken == inv.getArgument<String>(0) }
            }
            on { deleteAll(any()) } doAnswer { inv ->
                @Suppress("UNCHECKED_CAST")
                val toRemove = inv.getArgument<Iterable<SessionMachine>>(0).toSet()
                machines.removeAll(toRemove)
                Unit
            }
        }

        profileRepo = mock {
            on { save(any<UserProfile>()) } doAnswer { inv ->
                val p = inv.getArgument<UserProfile>(0)
                if (p.id == 0L) p.id = idGen.getAndIncrement()
                profiles[p.did] = p
                p
            }
            on { findByDid(any()) } doAnswer { inv ->
                profiles[inv.getArgument<String>(0)]
            }
            on { findByHandleIgnoreCase(any()) } doAnswer { inv ->
                val h = inv.getArgument<String>(0)
                profiles.values.find { it.handle.equals(h, ignoreCase = true) }
            }
        }

        tokenRepo = mock {
            on { save(any<UserShareableToken>()) } doAnswer { inv ->
                val t = inv.getArgument<UserShareableToken>(0)
                if (t.id == 0L) {
                    t.id = idGen.getAndIncrement()
                    tokens.add(t)
                }
                t
            }
            on { findByUserDidOrderByIsDefaultDescLastUsedAtDescIdDesc(any()) } doAnswer { inv ->
                val did = inv.getArgument<String>(0)
                tokens.filter { it.userDid == did }
            }
            on { findByUserDidAndToken(any(), any()) } doAnswer { inv ->
                val did = inv.getArgument<String>(0)
                val tok = inv.getArgument<String>(1)
                tokens.find { it.userDid == did && it.token == tok }
            }
            on { findFirstByTokenOrderByLastUsedAtDescIdDesc(any()) } doAnswer { inv ->
                val tok = inv.getArgument<String>(0)
                tokens.firstOrNull { it.token == tok }
            }
        }

        contractRepo = mock()
        blueSkyService = mock {
            on { resolveDidToHandle(any()) } doAnswer { inv ->
                profiles[inv.getArgument<String>(0)]?.handle ?: "unknown.bsky.social"
            }
        }
        botContext = mock {
            on { externalName } doAnswer { "b-overlord" }
            on { issueContract(any(), any(), any(), any(), any()) } doAnswer { inv ->
                Contract(
                    id = 42L,
                    botName = "b-overlord",
                    lockSessionToken = "ls-1",
                    shareableToken = inv.getArgument(0),
                    serialNumber = 1,
                    state = ContractState.CREATED,
                )
            }
        }

        val botMapService = mock<BotMapService> {
            on { getOrCreateBotMap(any(), any(), anyOrNull()) } doAnswer { inv ->
                BotMap(internalName = inv.getArgument(0), externalName = "b-overlord")
            }
        }
        val botComponent = mock<BotComponent> {
            on { contextFor(any<OverlordControlBot>()) } doAnswer { botContext }
        }
        val contractService = mock<ContractService>()

        userProfileService = UserProfileService(
            userProfileRepository = profileRepo,
            tokenRepository = tokenRepo,
            timeSource = timeSource,
            blueSkyService = blueSkyService,
        )

        val timerMachine = TimerMachine(timeSource)
        val proxyMachine = ProxyControlMachine()
        val likesMachine = BSkyLikesMachine(timeSource, blueSkyService)

        sessionEngine = SessionEngine(
            handlers = listOf(timerMachine, proxyMachine, likesMachine),
            sessionRepository = sessionRepo,
            machineRepository = machineRepo,
            contractService = contractService,
            userProfileService = userProfileService,
            urlService = UrlService("http://localhost:8090"),
            timeSource = timeSource,
            blueSkyService = blueSkyService,
        )

        overlordBot = OverlordControlBot(
            botMapService = botMapService,
            botComponent = botComponent,
            sessionEngine = sessionEngine,
            userProfileService = userProfileService,
            contractService = contractService,
            blueSkyService = blueSkyService,
            urlService = UrlService("http://localhost:8090"),
        )
    }

    @Test
    fun `UserProfileService learns multiple shareable tokens from DMs and resolves them`() {
        userProfileService.learnFromDm(
            did = "did:plc:sub1",
            convoId = "c-sub",
            handle = "sub.bsky.social",
            messageText = "Hey, my tokens are s-ABC1234 and tc-XYZ9876",
        )

        val learned = userProfileService.getTokensForUser("did:plc:sub1")
        assertEquals(2, learned.size)
        assertTrue(learned.any { it.token == "s-ABC1234" && it.tokenType == ShareableTokenType.S })
        assertTrue(learned.any { it.token == "tc-XYZ9876" && it.tokenType == ShareableTokenType.TC })

        val resolution = userProfileService.resolveTokenForUser("did:plc:sub1")
        assertTrue(resolution is TokenResolution.Multiple)
    }

    @Test
    fun `proxy_contract intent auto-injects known single token for target user`() {
        userProfileService.learnFromDm(
            did = "did:plc:sub1",
            convoId = "c-sub",
            handle = "blahblah.bsky.social",
            messageText = "My token is s-KNOWN01",
        )
        userProfileService.learnFromDm(
            did = "did:plc:dom1",
            convoId = "c-dom",
            handle = "dom.bsky.social",
            messageText = "hi",
        )

        val domContext = UserContextService(userProfileService, sessionRepo, machineRepo, contractRepo)
            .buildContextForDid("did:plc:dom1")
        val msg = mock<ConvoDefsMessageView> {
            on { sender } doAnswer { mock<ConvoDefsMessageViewSender> { on { did } doAnswer { "did:plc:dom1" } } }
        }
        val convoCtx = ConversationContext(userContext = domContext, convoId = "c-dom", currentMessage = msg)

        val proxyIntent = overlordBot.intents.first { it.name == "proxy_contract" }
        val call = proxyIntent.parseCall(
            mapOf(
                "otherUser" to kotlinx.serialization.json.JsonPrimitive("@blahblah.bsky.social"),
                "public" to kotlinx.serialization.json.JsonPrimitive(true),
            )
        )
        val response = call.execute(convoCtx)

        assertTrue(response.text.contains("s-KNOWN01"))
        verify(botContext).issueContract(
            shareableToken = eq("s-KNOWN01"),
            terms = any(),
            public = eq(true),
            temporaryUnlock = eq(false),
            coBots = any(),
        )
    }

    @Test
    fun `composable Timer and BSkyLikes machines only auto-release when ALL are satisfied`() {
        val session = sessionEngine.createDraftSession(
            subjectDid = "did:plc:sub1",
            convoId = "c-sub",
        )
        sessionEngine.issueSession(
            ctx = botContext,
            session = session,
            spec = SessionIssueSpec(
                shareableToken = "s-TOK1234",
                isPublic = false,
                releasePolicy = ReleasePolicy.ALL,
                machineTypes = listOf("timer", "bsky_likes"),
                timerConfig = TimerConfig(maxDuration = 1, maxUnit = "HOURS"),
            ),
        )

        val contract = Contract(
            id = 42L,
            botName = "b-overlord",
            lockSessionToken = "ls-1",
            shareableToken = "s-TOK1234",
            serialNumber = 1,
            state = ContractState.ACCEPTED,
        )

        overlordBot.onAccept(botContext, contract)
        assertEquals(SessionPhase.ACTIVE, session.phase)

        // Advance time past 1 hour so Timer is SATISFIED, while BSkyLikes is still WAITING_FORM
        now = now.plusHours(2)
        overlordBot.reviewContracts(botContext, listOf(contract))

        val sessionMachines = sessionEngine.findMachines(session.id)
        val timer = sessionMachines.first { it.machineType == "timer" }
        val likes = sessionMachines.first { it.machineType == "bsky_likes" }
        assertEquals(MachineStatus.SATISFIED, timer.status)
        assertEquals(MachineStatus.WAITING_FORM, likes.status)

        // Must NOT release yet because ReleasePolicy.ALL requires BSkyLikes too
        verify(botContext, never()).release(any())

        // Complete BSkyLikes
        likes.status = MachineStatus.SATISFIED
        (likes.state as BSkyLikesMachineState).likesSoFar = 100
        sessionEngine.saveMachine(likes)

        overlordBot.reviewContracts(botContext, listOf(contract))
        verify(botContext, times(1)).release(contract)
    }

    @Test
    fun `AnnouncerBot resolves handle via UserProfileService`() {
        userProfileService.learnFromDm("did:plc:sub1", "c-1", "alice.bsky.social", "s-ALICE01")
        val announcer = AnnouncerBot(
            botMapService = mock(),
            botComponent = mock(),
            userProfileService = userProfileService,
            blueSkyService = blueSkyService,
        )
        val msg = announcer.generateMessageForRelease("s-ALICE01")
        assertEquals("alice.bsky.social has been released", msg)
    }
}
