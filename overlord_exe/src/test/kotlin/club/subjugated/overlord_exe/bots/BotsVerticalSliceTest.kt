package club.subjugated.overlord_exe.bots

import club.subjugated.overlord_exe.bots.announcer.AnnouncerBot
import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.bots.simple_proxy.SimpleProxyBot
import club.subjugated.overlord_exe.bots.simple_proxy.SimpleProxyIntakeForm
import club.subjugated.overlord_exe.bots.timer_bot.TimeForm
import club.subjugated.overlord_exe.bots.timer_bot.TimerBot
import club.subjugated.overlord_exe.models.BSkyCrowdTimeObjectiveState
import club.subjugated.overlord_exe.models.BSkyLikesObjectiveState
import club.subjugated.overlord_exe.models.BSkyUser
import club.subjugated.overlord_exe.models.BotEngagement
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractState
import club.subjugated.overlord_exe.models.CrowdTimePhase
import club.subjugated.overlord_exe.models.EngagementPhase
import club.subjugated.overlord_exe.models.ObjectiveStatus
import club.subjugated.overlord_exe.models.SuperBotEngagementData
import club.subjugated.overlord_exe.models.TimerEngagementData
import club.subjugated.overlord_exe.objectives.BSkyCrowdTimeObjective
import club.subjugated.overlord_exe.objectives.BSkyLikesObjective
import club.subjugated.overlord_exe.objectives.ObjectiveService
import club.subjugated.overlord_exe.runtime.BotContext
import club.subjugated.overlord_exe.services.BSkyUserService
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.services.UrlService
import club.subjugated.overlord_exe.storage.BotEngagementRepository
import club.subjugated.overlord_exe.util.TimeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import java.time.ZoneOffset

class BotsVerticalSliceTest {

    private lateinit var botContext: BotContext
    private lateinit var botComponent: BotComponent
    private lateinit var botMapService: BotMapService
    private lateinit var contractService: ContractService
    private lateinit var fixedTime: OffsetDateTime
    private lateinit var timeSource: TimeSource
    private lateinit var engagements: MutableList<BotEngagement>
    private lateinit var engagementRepo: BotEngagementRepository

    @BeforeEach
    fun setUp() {
        fixedTime = OffsetDateTime.of(2026, 10, 24, 12, 0, 0, 0, ZoneOffset.UTC)
        timeSource = object : TimeSource() {
            override fun nowInUtc(): OffsetDateTime = fixedTime
        }
        botContext = mock {
            on { externalName } doAnswer { "b-TESTBOT" }
            on { internalName } doAnswer { "test" }
        }
        botComponent = mock {
            on { contextFor(any<club.subjugated.overlord_exe.runtime.OverlordBot>()) } doAnswer { botContext }
            on { contextFor(any<String>(), any<String>()) } doAnswer { botContext }
        }
        botMapService = mock()
        contractService = mock()

        engagements = mutableListOf()
        var nextId = 1L
        engagementRepo = mock {
            on { save(any<BotEngagement>()) } doAnswer { inv ->
                val e = inv.getArgument<BotEngagement>(0)
                if (e.id == 0L) e.id = nextId++
                e.syncJsonBeforeSave()
                engagements.removeIf { it.name == e.name }
                engagements.add(e)
                e
            }
            on { findByName(any()) } doAnswer { inv ->
                val name = inv.getArgument<String>(0)
                engagements.firstOrNull { it.name == name }
            }
            on { findByInfoToken(any()) } doAnswer { inv ->
                val token = inv.getArgument<String>(0)
                engagements.firstOrNull { it.infoToken == token }
            }
            on { findByStatusToken(any()) } doAnswer { inv ->
                val token = inv.getArgument<String>(0)
                engagements.firstOrNull { it.statusToken == token }
            }
            on { findByBotIdAndContractSerialNumber(any(), any()) } doAnswer { inv ->
                val botId = inv.getArgument<String>(0)
                val serial = inv.getArgument<Int>(1)
                engagements.firstOrNull { it.botId == botId && it.contractSerialNumber == serial }
            }
            on { findByBotIdAndContractIdIn(any(), any()) } doAnswer { inv ->
                val botId = inv.getArgument<String>(0)
                @Suppress("UNCHECKED_CAST")
                val ids = inv.getArgument<List<Long>>(1)
                engagements.filter { it.botId == botId && it.contractId in ids }
            }
            on { findByBotIdAndKeyholderDidAndSubjectDidAndPhaseIn(any(), any(), any(), any()) } doAnswer { inv ->
                val botId = inv.getArgument<String>(0)
                val khDid = inv.getArgument<String>(1)
                val subDid = inv.getArgument<String>(2)
                @Suppress("UNCHECKED_CAST")
                val phases = inv.getArgument<List<EngagementPhase>>(3)
                engagements.filter {
                    it.botId == botId &&
                        it.keyholderDid == khDid &&
                        it.subjectDid == subDid &&
                        it.phase in phases
                }
            }
        }
    }

    @Test
    fun `TimerBot issues contract via BotContext and releases expired contracts on review`() {
        val issuedContract = Contract(
            id = 42L,
            name = "bc-TIMER42",
            externalContractName = "c-EXT42",
            botName = "b-TIMER",
            lockSessionToken = "ls-1",
            shareableToken = "s-TOK1234",
            serialNumber = 777,
            state = ContractState.CREATED,
        )
        whenever(
            botContext.issueContract(
                shareableToken = eq("s-TOK1234"),
                terms = any(),
                public = eq(true),
                temporaryUnlock = any(),
                coBots = any(),
            )
        ).thenReturn(issuedContract)

        val timerBot = TimerBot(
            engagementRepository = engagementRepo,
            timeSource = timeSource,
            botMapService = botMapService,
            botComponent = botComponent,
        )

        val placeholder = timerBot.createInitialPlaceholderSession("did:plc:user1", "convo-1")
        val form = TimeForm(
            name = placeholder.name,
            shareableToken = "s-TOK1234",
            random = false,
            public = true,
            maxDuration = 2,
            maxUnit = "HOURS",
        )

        val issuedEngagement = timerBot.processContractForm(form)
        assertEquals(EngagementPhase.ISSUED, issuedEngagement.phase)
        assertEquals(777, issuedEngagement.contractSerialNumber)
        assertEquals(42L, issuedEngagement.contractId)
        verify(botContext).sendDm("convo-1", "Contract issued")

        // Simulate contract acceptance
        timerBot.onAccept(botContext, issuedContract)
        val timerData = issuedEngagement.data as TimerEngagementData
        assertEquals(fixedTime.plusHours(2), timerData.endsAt)

        // Advance time past expiration and review
        fixedTime = fixedTime.plusHours(3)
        timerBot.reviewContracts(botContext, listOf(issuedContract))

        verify(botContext).addMessage(issuedContract, "Time is up.")
        verify(botContext).release(issuedContract)
    }

    @Test
    fun `SimpleProxyBot issues contract and synchronously executes lock unlock and release via BotContext`() {
        val keyHolder = BSkyUser(id = 1L, did = "did:plc:kh", handle = "@kh.bsky.social", convoId = "convo-kh")
        val sub = BSkyUser(id = 2L, did = "did:plc:sub", handle = "@sub.bsky.social", convoId = "convo-sub", shareableToken = "s-SUB7777")

        val contract = Contract(
            id = 99L,
            name = "bc-PROXY99",
            externalContractName = "c-PROXY99",
            botName = "b-TESTBOT",
            lockSessionToken = "ls-PROXY",
            shareableToken = "s-SUB7777",
            serialNumber = 888,
            state = ContractState.ACCEPTED,
        )
        whenever(botContext.issueContract(eq("s-SUB7777"), any(), eq(false), any(), any())).thenReturn(contract)
        whenever(contractService.getContract("b-TESTBOT", 888)).thenReturn(contract)

        val service = SimpleProxyBot(
            engagementRepository = engagementRepo,
            botMapService = botMapService,
            botComponent = botComponent,
            contractService = contractService,
            timeSource = timeSource,
        )

        val engagement = service.createInitialRecord(keyHolder, sub)
        service.processIntakeForm(SimpleProxyIntakeForm(name = engagement.name, public = false))

        assertEquals(EngagementPhase.ISSUED, engagement.phase)
        assertEquals(888, engagement.contractSerialNumber)
        assertEquals(99L, engagement.contractId)
        verify(botContext).sendDm("convo-kh", "Contract issued")
        verify(botContext).sendDm("convo-sub", "You have been issued a contract by @kh.bsky.social")

        // Accept contract
        service.onAccept(botContext, contract)
        assertEquals(EngagementPhase.ACTIVE, engagement.phase)
        verify(botContext).sendDm("convo-kh", "Contract accepted")

        // Synchronously unlock, lock, and release via BotContext
        assertTrue(service.unlock(keyHolder, sub).isSuccess)
        verify(botContext).unlock(contract)

        assertTrue(service.lock(keyHolder, sub).isSuccess)
        verify(botContext).lock(contract)

        assertTrue(service.release(keyHolder, sub).isSuccess)
        verify(botContext).release(contract)
    }

    @Test
    fun `ObjectiveService manages bsky_likes and bsky_crowd_time objectives inside BotEngagement JSON`() {
        val blueSkyService = mock<BlueSkyService> {
            on { resolveDidToHandle(any()) } doAnswer { "@subject.bsky.social" }
            on { post(any()) } doAnswer { "at://did:plc:overlord/app.bsky.feed.post/notice1" }
        }
        val urlService = UrlService("http://localhost:8090")
        val likesObj = BSkyLikesObjective(timeSource, blueSkyService)
        val crowdTimeObj = BSkyCrowdTimeObjective(timeSource, blueSkyService, urlService)

        val objectiveService = ObjectiveService(
            objectives = listOf(likesObj, crowdTimeObj),
            engagementRepository = engagementRepo,
            blueSkyService = blueSkyService,
            urlService = urlService,
            timeSource = timeSource,
        )

        val engagement = BotEngagement(
            name = "sb-TEST1",
            botId = "superBot",
            phase = EngagementPhase.ACTIVE,
            subjectDid = "did:plc:sub1",
            convoId = "convo-sub1",
            createdAt = fixedTime,
            data = SuperBotEngagementData(objectives = listOf("bsky_crowd_time")),
        )
        engagementRepo.save(engagement)

        val initialState = objectiveService.startObjective(engagement, "bsky_crowd_time") as BSkyCrowdTimeObjectiveState
        assertEquals(ObjectiveStatus.WAITING, initialState.status)
        val infoToken = engagement.infoToken!!
        verify(blueSkyService).sendDm("convo-sub1", "http://localhost:8090/info/$infoToken")

        // Submit info form
        objectiveService.submitInfoForm(
            mapOf(
                "name" to infoToken,
                "perLikeAdd" to "15",
                "perLikeAddUnit" to "MINUTES",
                "perRepostAdd" to "2",
                "perRepostAddUnit" to "HOURS",
                "openPeriodAmount" to "3",
                "openPeriodUnit" to "HOURS",
            )
        )

        assertNull(engagement.infoToken)
        assertEquals(ObjectiveStatus.IN_PROGRESS, initialState.status)
        assertEquals(CrowdTimePhase.OPEN_POSTED, initialState.phase)
        assertEquals("at://did:plc:overlord/app.bsky.feed.post/notice1", initialState.noticeUri)
    }

    @Test
    fun `AnnouncerBot has null reviewInterval and posts on contract accept and release`() {
        val blueSkyService = mock<BlueSkyService> {
            on { resolveDidToHandle("did:plc:sub1") } doAnswer { "@sub1.bsky.social" }
        }
        val bskyUserService = mock<BSkyUserService> {
            on { findByShareableToken("s-TOK9999") } doAnswer {
                BSkyUser(did = "did:plc:sub1", handle = "@sub1.bsky.social", shareableToken = "s-TOK9999")
            }
        }

        val announcer = AnnouncerBot(
            botMapService = botMapService,
            botComponent = botComponent,
            bskyUserService = bskyUserService,
            timeSource = timeSource,
            blueSkyService = blueSkyService,
        )

        assertNull(announcer.descriptor.reviewInterval)

        val contract = Contract(
            id = 5L,
            botName = "b-ANNOUNCER",
            lockSessionToken = "ls-5",
            shareableToken = "s-TOK9999",
            serialNumber = 500,
        )
        announcer.onAccept(botContext, contract)
        verify(blueSkyService).post("@sub1.bsky.social has accepted a contract.")

        announcer.onRelease(botContext, contract)
        verify(blueSkyService).post("@sub1.bsky.social has been released")
    }
}
