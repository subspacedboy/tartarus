package club.subjugated.overlord_exe.bots

import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.bots.simple_proxy.SimpleProxy
import club.subjugated.overlord_exe.bots.simple_proxy.SimpleProxyBot
import club.subjugated.overlord_exe.bots.simple_proxy.SimpleProxyRepository
import club.subjugated.overlord_exe.bots.simple_proxy.SimpleProxyState
import club.subjugated.overlord_exe.bots.simple_proxy.web.SimpleProxyIntakeForm
import club.subjugated.overlord_exe.bots.timer_bot.TimerBot
import club.subjugated.overlord_exe.bots.timer_bot.TimerSession
import club.subjugated.overlord_exe.bots.timer_bot.TimerSessionRepository
import club.subjugated.overlord_exe.bots.timer_bot.TimerSessionState
import club.subjugated.overlord_exe.bots.timer_bot.web.TimeForm
import club.subjugated.overlord_exe.models.BSkyUser
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractState
import club.subjugated.overlord_exe.runtime.BotContext
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
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
    }

    @Test
    fun `TimerBot issues contract via BotContext and releases expired contracts on review`() {
        val sessions = mutableListOf<TimerSession>()
        val repo = mock<TimerSessionRepository> {
            on { save(any<TimerSession>()) } doAnswer { inv ->
                val r = inv.getArgument<TimerSession>(0)
                if (r.id == 0L) r.id = 1L
                sessions.removeIf { it.name == r.name }
                sessions.add(r)
                r
            }
            on { findByName(any()) } doAnswer { inv ->
                val name = inv.getArgument<String>(0)
                sessions.first { it.name == name }
            }
            on { findByContractSerialNumber(any()) } doAnswer { inv ->
                val serial = inv.getArgument<Long>(0).toInt()
                sessions.first { it.contractSerialNumber == serial }
            }
            on { findByContractIdIn(any()) } doAnswer { inv ->
                @Suppress("UNCHECKED_CAST")
                val ids = inv.getArgument<List<Long>>(0)
                sessions.filter { it.contractId in ids }
            }
        }

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
            timerSessionRepository = repo,
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

        val issuedSession = timerBot.processContractForm(form)
        assertEquals(TimerSessionState.ISSUED, issuedSession.state)
        assertEquals(777, issuedSession.contractSerialNumber)
        assertEquals(42L, issuedSession.contractId)
        verify(botContext).sendDm("convo-1", "Contract issued")

        // Simulate contract acceptance
        timerBot.onAccept(botContext, issuedContract)
        assertEquals(fixedTime.plusHours(2), issuedSession.endsAt)

        // Advance time past expiration and review
        fixedTime = fixedTime.plusHours(3)
        timerBot.reviewContracts(botContext, listOf(issuedContract))

        verify(botContext).addMessage(issuedContract, "Time is up.")
        verify(botContext).release(issuedContract)
    }

    @Test
    fun `SimpleProxyBot issues contract and synchronously executes lock unlock and release via BotContext`() {
        val proxies = mutableListOf<SimpleProxy>()
        val repo = mock<SimpleProxyRepository> {
            on { save(any<SimpleProxy>()) } doAnswer { inv ->
                val p = inv.getArgument<SimpleProxy>(0)
                if (p.id == 0L) p.id = 10L
                proxies.removeIf { it.name == p.name }
                proxies.add(p)
                p
            }
            on { findByName(any()) } doAnswer { inv ->
                val name = inv.getArgument<String>(0)
                proxies.first { it.name == name }
            }
            on { findByContractSerialNumber(any()) } doAnswer { inv ->
                val serial = inv.getArgument<Long>(0).toInt()
                proxies.first { it.contractSerialNumber == serial }
            }
            on { findByBskyUserAndKeyHolderBskyUserAndStateIn(any(), any(), any()) } doAnswer { inv ->
                val sub = inv.getArgument<BSkyUser>(0)
                val kh = inv.getArgument<BSkyUser>(1)
                @Suppress("UNCHECKED_CAST")
                val states = inv.getArgument<List<SimpleProxyState>>(2)
                proxies.filter { it.bskyUser.did == sub.did && it.keyHolderBskyUser.did == kh.did && it.state in states }
            }
        }

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
            simpleProxyRepository = repo,
            botMapService = botMapService,
            botComponent = botComponent,
            contractService = contractService,
            timeSource = timeSource,
        )

        val record = service.createInitialRecord(keyHolder, sub)
        service.processIntakeForm(SimpleProxyIntakeForm(name = record.name, public = false))

        assertEquals(SimpleProxyState.ISSUED, record.state)
        assertEquals(888, record.contractSerialNumber)
        assertEquals(99L, record.contractId)
        verify(botContext).sendDm("convo-kh", "Contract issued")
        verify(botContext).sendDm("convo-sub", "You have been issued a contract by @kh.bsky.social")

        // Accept contract
        service.onAccept(botContext, contract)
        assertEquals(SimpleProxyState.ACCEPTED, record.state)
        verify(botContext).sendDm("convo-kh", "Contract accepted")

        // Synchronously unlock, lock, and release via BotContext
        assertTrue(service.unlock(keyHolder, sub).isSuccess)
        verify(botContext).unlock(contract)

        assertTrue(service.lock(keyHolder, sub).isSuccess)
        verify(botContext).lock(contract)

        assertTrue(service.release(keyHolder, sub).isSuccess)
        verify(botContext).release(contract)
    }
}
