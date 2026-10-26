package club.subjugated.overlord_exe.runtime

import club.subjugated.fb.message.Contract as FbContract
import club.subjugated.fb.message.LockCommand
import club.subjugated.fb.message.MessagePayload
import club.subjugated.fb.message.ReleaseCommand
import club.subjugated.fb.message.SignedMessage
import club.subjugated.fb.message.UnlockCommand
import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractState
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.BotContractInfo
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.services.NewBotResponse
import club.subjugated.overlord_exe.storage.BotMapRepository
import club.subjugated.overlord_exe.storage.ContractRepository
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.deriveBotKeyPair
import club.subjugated.overlord_exe.util.encodePublicKeySecp1
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.env.Environment
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.nio.ByteBuffer
import java.security.interfaces.ECPublicKey
import java.time.Duration
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong

class BotRuntimeArchitectureTest {

    private val authorKeyB64 =
        "MHcCAQEEICOlEjRS6mUr9zp2KiPi5gsH9tpefhcz6A/eJ4E9YYlvoAoGCCqGSM49AwEHoUQDQgAEvAV5D//RrShzJycWplQRvh44rJhzkPb1Mprq70K32yrKm2Txyup1OTlHQK2fvF6Ne+Mghfue0oGlYpLLaYIErg=="

    private lateinit var botMapRepository: BotMapRepository
    private lateinit var contractRepository: ContractRepository
    private lateinit var coordinatorGateway: CoordinatorGateway
    private lateinit var environment: Environment
    private lateinit var blueSkyService: BlueSkyService
    private lateinit var transactionTemplate: TransactionTemplate
    private lateinit var botMapService: BotMapService
    private lateinit var contractService: ContractService
    private lateinit var botComponent: BotComponent

    private val savedBotMaps = mutableMapOf<String, BotMap>()
    private val savedContracts = mutableListOf<Contract>()
    private val idCounter = AtomicLong(1L)

    @BeforeEach
    fun setUp() {
        savedBotMaps.clear()
        savedContracts.clear()

        botMapRepository = mock {
            on { findByInternalNameAndCoordinator(any(), any()) } doAnswer { inv ->
                val internalName = inv.getArgument<String>(0)
                savedBotMaps[internalName]
            }
            on { save(any<BotMap>()) } doAnswer { inv ->
                val bm = inv.getArgument<BotMap>(0)
                if (bm.id == 0L) bm.id = idCounter.getAndIncrement()
                savedBotMaps[bm.internalName] = bm
                bm
            }
        }

        contractRepository = mock {
            on { findByBotNameAndSerialNumber(any(), any()) } doAnswer { inv ->
                val botName = inv.getArgument<String>(0)
                val serial = inv.getArgument<Int>(1)
                savedContracts.find { it.botName == botName && it.serialNumber == serial }
            }
            on { findByBotNameAndStateIn(any(), any()) } doAnswer { inv ->
                val botName = inv.getArgument<String>(0)
                @Suppress("UNCHECKED_CAST")
                val states = inv.getArgument<List<ContractState>>(1)
                savedContracts.filter { it.botName == botName && it.state in states }
            }
            on { save(any<Contract>()) } doAnswer { inv ->
                val c = inv.getArgument<Contract>(0)
                if (c.id == 0L) {
                    c.id = idCounter.getAndIncrement()
                    savedContracts.add(c)
                } else {
                    val idx = savedContracts.indexOfFirst { it.id == c.id }
                    if (idx >= 0) savedContracts[idx] = c else savedContracts.add(c)
                }
                c
            }
        }

        coordinatorGateway = mock()
        environment = mock {
            on { activeProfiles } doAnswer { emptyArray<String>() }
        }
        blueSkyService = mock()

        transactionTemplate = mock {
            on { execute(any<TransactionCallback<Any?>>()) } doAnswer { inv ->
                val callback = inv.getArgument<TransactionCallback<Any?>>(0)
                val status = mock<TransactionStatus>()
                callback.doInTransaction(status)
            }
        }

        val timeSource = TimeSource()
        val eventPublisher = mock<ApplicationEventPublisher>()

        botMapService = BotMapService(
            botMapRepository = botMapRepository,
            coordinator = "http://localhost:5002",
            authorPrivateKeyB64 = authorKeyB64,
            timeSource = timeSource,
            environment = environment,
            coordinatorGateway = coordinatorGateway,
        )

        contractService = ContractService(
            contractRepository = contractRepository,
            botMapService = botMapService,
            timeSource = timeSource,
            applicationEventPublisher = eventPublisher,
            environment = environment,
        )

        botComponent = BotComponent(
            contractService = contractService,
            botMapService = botMapService,
            transactionTemplate = transactionTemplate,
            environment = environment,
            coordinatorGateway = coordinatorGateway,
            blueSkyService = blueSkyService,
            discoveredBots = emptyList(),
            coordinator = "http://localhost:5002",
            wsUri = "ws://localhost:5002/ws/events",
        )
    }

    @Test
    fun `BotMapService honors rotated keyVersion from Coordinator getMyBots`() {
        whenever(coordinatorGateway.createAuthorSession(any())).thenReturn(
            club.subjugated.overlord_exe.services.AuthorSessionResponse(
                sessionToken = "as-AUTHOR1",
                name = "as-AUTHOR1",
            )
        )
        whenever(coordinatorGateway.getMyBots(any())).thenReturn(
            listOf(
                CoordinatorBotDto(
                    name = "b-TIMER-V3",
                    internalName = "timer",
                    keyVersion = 3,
                    state = "ACTIVE",
                )
            )
        )
        whenever(coordinatorGateway.registerBot(any(), any())).thenReturn(
            NewBotResponse(name = "b-TIMER-V3", clearTextPassword = "")
        )

        val botMap = botMapService.getOrCreateBotMap("timer", "TimerBot")

        assertEquals("b-TIMER-V3", botMap.externalName)
        val expectedKeyPairV3 = deriveBotKeyPair(botMapService.authorKeyPair.private, "timer", 3)
        assertArrayEquals(expectedKeyPairV3.public.encoded, botMap.publicKey)

        val regCaptor = argumentCaptor<club.subjugated.overlord_exe.services.NewBotRequest>()
        verify(coordinatorGateway).registerBot(any(), regCaptor.capture())
        assertEquals(3, regCaptor.firstValue.keyVersion)
        val expectedCompressedB64 = Base64.getEncoder().encodeToString(
            encodePublicKeySecp1(expectedKeyPairV3.public as ECPublicKey)
        )
        assertEquals(expectedCompressedB64, regCaptor.firstValue.publicKey)
    }

    @Test
    fun `BotContext issueContract persists Contract immediately and includes announcer CoBot when public`() {
        whenever(coordinatorGateway.createAuthorSession(any())).thenReturn(
            club.subjugated.overlord_exe.services.AuthorSessionResponse(name = "as-AUTHOR1")
        )
        whenever(coordinatorGateway.getMyBots(any())).thenReturn(emptyList())
        whenever(coordinatorGateway.registerBot(any(), any())).thenAnswer { inv ->
            val req = inv.getArgument<club.subjugated.overlord_exe.services.NewBotRequest>(1)
            NewBotResponse(name = "b-${req.internalName?.uppercase()}")
        }
        whenever(coordinatorGateway.createContract(any(), any(), any(), any(), any())).thenAnswer { inv ->
            val token = inv.getArgument<String>(2)
            val signedB64 = inv.getArgument<String>(3)
            val serial = inv.getArgument<Int>(4)
            BotContractInfo(
                name = "c-EXT-999",
                shareableToken = token,
                state = "CREATED",
                body = signedB64,
                nextCounter = 1,
                serialNumber = serial,
                lockSessionToken = "ls-LOCK1",
            )
        }

        // Register announcer and timer bots
        botMapService.getOrCreateBotMap("announcer", "Announcer bot")
        val timerCtx = botComponent.contextFor("timer", "TimerBot")

        val contract = timerCtx.issueContract(
            shareableToken = "s-SUB1234",
            terms = "Timer bot: 2 hours",
            public = true,
        )

        assertTrue(contract.id > 0L)
        assertEquals("c-EXT-999", contract.externalContractName)
        assertEquals("s-SUB1234", contract.shareableToken)
        assertEquals("ls-LOCK1", contract.lockSessionToken)
        assertEquals(ContractState.CREATED, contract.state)
        assertEquals(1, contract.nextCounter)
        assertNotNull(contract.signedMessage)

        // Inspect the FlatBuffer Contract to verify both b-TIMER and b-ANNOUNCER are in the bots vector
        val sm = SignedMessage.getRootAsSignedMessage(ByteBuffer.wrap(contract.signedMessage!!))
        assertEquals(MessagePayload.Contract, sm.payloadType)
        val fbContract = FbContract()
        sm.payload(fbContract)
        assertEquals("Timer bot: 2 hours", fbContract.terms)
        assertEquals(2, fbContract.botsLength)

        val botNames = (0 until fbContract.botsLength).map { idx -> fbContract.bots(idx)!!.name }
        assertTrue(botNames.contains("b-TIMER"))
        assertTrue(botNames.contains("b-ANNOUNCER"))

        val announcerEntry = (0 until fbContract.botsLength)
            .map { fbContract.bots(it)!! }
            .first { it.name == "b-ANNOUNCER" }
        assertEquals(true, announcerEntry.permissions!!.receiveEvents)
        assertEquals(false, announcerEntry.permissions!!.canUnlock)
        assertEquals(false, announcerEntry.permissions!!.canRelease)
    }

    @Test
    fun `BotContext unlock lock and release sync nextCounter from Coordinator and sign commands`() {
        whenever(coordinatorGateway.createAuthorSession(any())).thenReturn(
            club.subjugated.overlord_exe.services.AuthorSessionResponse(name = "as-AUTHOR1")
        )
        whenever(coordinatorGateway.getMyBots(any())).thenReturn(emptyList())
        whenever(coordinatorGateway.registerBot(any(), any())).thenReturn(
            NewBotResponse(name = "b-PROXY")
        )

        val proxyCtx = botComponent.contextFor("simple_proxy", "Simple proxy")
        val contract = Contract(
            id = 10L,
            name = "bc-LOCAL1",
            externalContractName = "c-REMOTE1",
            botName = "b-PROXY",
            lockSessionToken = "ls-SESSION1",
            shareableToken = "s-TOKEN1",
            serialNumber = 4321,
            state = ContractState.ACCEPTED,
        )
        savedContracts.add(contract)

        whenever(coordinatorGateway.getContractBySession(eq("b-PROXY"), any(), eq("ls-SESSION1"), eq(4321)))
            .thenReturn(
                BotContractInfo(
                    name = "c-REMOTE1",
                    shareableToken = "s-TOKEN1",
                    state = "ACCEPTED",
                    nextCounter = 7,
                    serialNumber = 4321,
                    lockSessionToken = "ls-SESSION1",
                )
            )

        proxyCtx.unlock(contract)

        val cmdCaptor = argumentCaptor<String>()
        verify(coordinatorGateway).createCommand(
            eq("b-PROXY"),
            any(),
            eq("c-REMOTE1"),
            eq("s-TOKEN1"),
            cmdCaptor.capture(),
        )

        val signedBytes = Base64.getDecoder().decode(cmdCaptor.firstValue)
        val sm = SignedMessage.getRootAsSignedMessage(ByteBuffer.wrap(signedBytes))
        assertEquals(MessagePayload.UnlockCommand, sm.payloadType)
        assertEquals("b-PROXY", sm.authorityIdentifier)
        val unlockCmd = UnlockCommand()
        sm.payload(unlockCmd)
        assertEquals(4321, unlockCmd.contractSerialNumber.toInt())
        assertEquals(7, unlockCmd.counter.toInt())

        // Counter is incremented after command
        assertEquals(8, contractService.getCounter(contract))
    }

    @Test
    fun `BotComponent reviewAllBotsIsolated skips non-polling bots, marks ABORTED contracts, and isolates failures`() {
        whenever(coordinatorGateway.createAuthorSession(any())).thenReturn(
            club.subjugated.overlord_exe.services.AuthorSessionResponse(name = "as-AUTHOR1")
        )
        whenever(coordinatorGateway.getMyBots(any())).thenReturn(emptyList())
        whenever(coordinatorGateway.registerBot(any(), any())).thenAnswer { inv ->
            val req = inv.getArgument<club.subjugated.overlord_exe.services.NewBotRequest>(1)
            NewBotResponse(name = "b-${req.internalName?.uppercase()}")
        }

        var observerReviewed = false
        val observerBot = object : OverlordBot {
            override val descriptor = BotDescriptor("announcer", "Observer", reviewInterval = null)
            override fun reviewContracts(ctx: BotContext, contracts: List<Contract>) {
                observerReviewed = true
            }
        }

        val abortedHandled = mutableListOf<Contract>()
        val activeReviewed = mutableListOf<Contract>()
        val timerBot = object : OverlordBot {
            override val descriptor = BotDescriptor("timer", "Timer", reviewInterval = Duration.ofMinutes(1))
            override fun onAbort(ctx: BotContext, contract: Contract) {
                abortedHandled.add(contract)
            }
            override fun reviewContracts(ctx: BotContext, contracts: List<Contract>) {
                activeReviewed.addAll(contracts)
            }
        }

        val failingBot = object : OverlordBot {
            override val descriptor = BotDescriptor("failing", "Failing", reviewInterval = Duration.ofMinutes(1))
            override fun reviewContracts(ctx: BotContext, contracts: List<Contract>) {
                throw RuntimeException("Simulated bot failure")
            }
        }

        val announcerMap = botMapService.getOrCreateBotMap("announcer", "Observer")
        val timerMap = botMapService.getOrCreateBotMap("timer", "Timer")
        val failingMap = botMapService.getOrCreateBotMap("failing", "Failing")

        botComponent.startBot(announcerMap, observerBot)
        botComponent.startBot(timerMap, timerBot)
        botComponent.startBot(failingMap, failingBot)

        val cAborted = Contract(
            id = 1L,
            name = "bc-1",
            externalContractName = "c-1",
            botName = "b-TIMER",
            lockSessionToken = "ls-1",
            serialNumber = 101,
            state = ContractState.ACCEPTED,
        )
        val cActive = Contract(
            id = 2L,
            name = "bc-2",
            externalContractName = "c-2",
            botName = "b-TIMER",
            lockSessionToken = "ls-2",
            serialNumber = 102,
            state = ContractState.ACCEPTED,
        )
        savedContracts.add(cAborted)
        savedContracts.add(cActive)

        whenever(coordinatorGateway.getContractBySession(eq("b-TIMER"), any(), eq("ls-1"), eq(101)))
            .thenReturn(BotContractInfo(name = "c-1", state = "ABORTED", nextCounter = 2))
        whenever(coordinatorGateway.getContractBySession(eq("b-TIMER"), any(), eq("ls-2"), eq(102)))
            .thenReturn(BotContractInfo(name = "c-2", state = "ACCEPTED", nextCounter = 5))

        botComponent.reviewAllBotsIsolated()

        // 1. Observer bot with reviewInterval = null was skipped
        assertEquals(false, observerReviewed)

        // 2. Aborted contract was marked ABORTED before reviewContracts, onAbort was called, and only cActive was passed to reviewContracts
        assertEquals(ContractState.ABORTED, cAborted.state)
        assertEquals(1, abortedHandled.size)
        assertEquals(101, abortedHandled.first().serialNumber)
        assertEquals(1, activeReviewed.size)
        assertEquals(102, activeReviewed.first().serialNumber)
        assertEquals(5, activeReviewed.first().nextCounter)
    }
}
