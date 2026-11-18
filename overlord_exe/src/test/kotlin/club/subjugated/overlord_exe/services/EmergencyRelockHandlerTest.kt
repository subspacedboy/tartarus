package club.subjugated.overlord_exe.services

import club.subjugated.fb.event.CommonMetadata
import club.subjugated.fb.event.EmergencyUnlock
import club.subjugated.fb.event.Event
import club.subjugated.fb.event.EventType
import club.subjugated.fb.event.Lock
import club.subjugated.fb.event.PeriodicUpdate
import club.subjugated.fb.event.SignedEvent
import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.bots.superbot.SuperBot
import club.subjugated.overlord_exe.bots.timer_bot.TimerBot
import club.subjugated.overlord_exe.convo.ConversationContext
import club.subjugated.overlord_exe.intents.OverlordConversationHandler
import club.subjugated.overlord_exe.models.BSkyUser
import club.subjugated.overlord_exe.models.BotEngagement
import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractState
import club.subjugated.overlord_exe.models.EngagementPhase
import club.subjugated.overlord_exe.models.ObjectiveStatus
import club.subjugated.overlord_exe.models.SuperBotEngagementData
import club.subjugated.overlord_exe.models.TimerEngagementData
import club.subjugated.overlord_exe.objectives.ObjectiveService
import club.subjugated.overlord_exe.runtime.BotContext
import club.subjugated.overlord_exe.runtime.BotDescriptor
import club.subjugated.overlord_exe.runtime.CoordinatorGateway
import club.subjugated.overlord_exe.runtime.OverlordBot
import club.subjugated.overlord_exe.storage.BotEngagementRepository
import club.subjugated.overlord_exe.storage.BotMapRepository
import club.subjugated.overlord_exe.storage.ContractRepository
import club.subjugated.overlord_exe.util.TimeSource
import com.google.flatbuffers.FlatBufferBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.core.env.Environment
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageView
import work.socialhub.kbsky.model.chat.bsky.convo.ConvoDefsMessageViewSender
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

class EmergencyRelockHandlerTest {

    private val authorKeyB64 =
        "MHcCAQEEICOlEjRS6mUr9zp2KiPi5gsH9tpefhcz6A/eJ4E9YYlvoAoGCCqGSM49AwEHoUQDQgAEvAV5D//RrShzJycWplQRvh44rJhzkPb1Mprq70K32yrKm2Txyup1OTlHQK2fvF6Ne+Mghfue0oGlYpLLaYIErg=="

    private lateinit var botMapRepository: BotMapRepository
    private lateinit var contractRepository: ContractRepository
    private lateinit var botEngagementRepository: BotEngagementRepository
    private lateinit var coordinatorGateway: CoordinatorGateway
    private lateinit var environment: Environment
    private lateinit var blueSkyService: BlueSkyService
    private lateinit var bSkyUserService: BSkyUserService
    private lateinit var transactionTemplate: TransactionTemplate
    private lateinit var botMapService: BotMapService
    private lateinit var contractService: ContractService
    private lateinit var botComponent: BotComponent
    private lateinit var emergencyRelockService: EmergencyRelockService

    private val savedBotMaps = mutableMapOf<String, BotMap>()
    private val savedContracts = mutableListOf<Contract>()
    private val savedEngagements = mutableListOf<BotEngagement>()
    private val idCounter = AtomicLong(1L)
    private lateinit var fixedTime: OffsetDateTime
    private lateinit var timeSource: TimeSource

    @BeforeEach
    fun setUp() {
        savedBotMaps.clear()
        savedContracts.clear()
        savedEngagements.clear()

        fixedTime = OffsetDateTime.of(2026, 11, 18, 12, 0, 0, 0, ZoneOffset.UTC)
        timeSource = object : TimeSource() {
            override fun nowInUtc(): OffsetDateTime = fixedTime
        }

        botMapRepository = mock {
            on { findByInternalNameAndCoordinator(any(), any()) } doAnswer { inv ->
                savedBotMaps[inv.getArgument<String>(0)]
            }
            on { save(any<BotMap>()) } doAnswer { inv ->
                val bm = inv.getArgument<BotMap>(0)
                if (bm.id == 0L) bm.id = idCounter.getAndIncrement()
                savedBotMaps[bm.internalName] = bm
                bm
            }
        }

        contractRepository = mock {
            on { findById(any()) } doAnswer { inv ->
                val id = inv.getArgument<Long>(0)
                Optional.ofNullable(savedContracts.find { it.id == id })
            }
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
            on { findByShareableToken(any()) } doAnswer { inv ->
                val token = inv.getArgument<String>(0)
                savedContracts.filter { it.shareableToken == token }
            }
            on { findByEmergencyUnlockedTrueAndStateIn(any()) } doAnswer { inv ->
                @Suppress("UNCHECKED_CAST")
                val states = inv.getArgument<List<ContractState>>(0)
                savedContracts.filter { it.emergencyUnlocked && it.state in states }
            }
            on { findByShareableTokenAndEmergencyUnlockedTrueAndStateIn(any(), any()) } doAnswer { inv ->
                val token = inv.getArgument<String>(0)
                @Suppress("UNCHECKED_CAST")
                val states = inv.getArgument<List<ContractState>>(1)
                savedContracts.filter {
                    it.shareableToken == token && it.emergencyUnlocked && it.state in states
                }
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

        botEngagementRepository = mock {
            on { findByContractId(any()) } doAnswer { inv ->
                val cid = inv.getArgument<Long>(0)
                savedEngagements.find { it.contractId == cid }
            }
            on { findByBotIdAndContractSerialNumber(any(), any()) } doAnswer { inv ->
                val botId = inv.getArgument<String>(0)
                val serial = inv.getArgument<Int>(1)
                savedEngagements.find { it.botId == botId && it.contractSerialNumber == serial }
            }
            on { findBySubjectDidAndPhase(any(), any()) } doAnswer { inv ->
                val did = inv.getArgument<String>(0)
                val phase = inv.getArgument<EngagementPhase>(1)
                savedEngagements.filter { it.subjectDid == did && it.phase == phase }
            }
            on { findByConvoIdAndPhase(any(), any()) } doAnswer { inv ->
                val convoId = inv.getArgument<String>(0)
                val phase = inv.getArgument<EngagementPhase>(1)
                savedEngagements.filter { it.convoId == convoId && it.phase == phase }
            }
            on { findBySubjectDidAndPhaseIn(any(), any()) } doAnswer { inv ->
                val did = inv.getArgument<String>(0)
                @Suppress("UNCHECKED_CAST")
                val phases = inv.getArgument<List<EngagementPhase>>(1)
                savedEngagements.filter { it.subjectDid == did && it.phase in phases }
            }
            on { findByConvoIdAndPhaseIn(any(), any()) } doAnswer { inv ->
                val convoId = inv.getArgument<String>(0)
                @Suppress("UNCHECKED_CAST")
                val phases = inv.getArgument<List<EngagementPhase>>(1)
                savedEngagements.filter { it.convoId == convoId && it.phase in phases }
            }
            on { findByBotIdAndContractIdIn(any(), any()) } doAnswer { inv ->
                val botId = inv.getArgument<String>(0)
                @Suppress("UNCHECKED_CAST")
                val ids = inv.getArgument<List<Long>>(1)
                savedEngagements.filter { it.botId == botId && it.contractId in ids }
            }
        }

        coordinatorGateway = mock()
        environment = mock {
            on { activeProfiles } doAnswer { emptyArray<String>() }
        }
        blueSkyService = mock()
        bSkyUserService = mock()

        transactionTemplate = mock {
            on { execute(any<TransactionCallback<Any?>>()) } doAnswer { inv ->
                val callback = inv.getArgument<TransactionCallback<Any?>>(0)
                callback.doInTransaction(mock<TransactionStatus>())
            }
        }

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
            botEngagementRepository = botEngagementRepository,
            bSkyUserService = bSkyUserService,
        )

        emergencyRelockService = EmergencyRelockService(
            contractService = contractService,
            botEngagementRepository = botEngagementRepository,
            botComponent = botComponent,
        )
    }

    private fun registerBot(internalName: String, externalName: String): BotMap {
        whenever(coordinatorGateway.createAuthorSession(any())).thenReturn(
            AuthorSessionResponse(sessionToken = "as-1", name = "as-AUTHOR1")
        )
        whenever(coordinatorGateway.getMyBots(any())).thenReturn(emptyList())
        whenever(
            coordinatorGateway.registerBot(
                any(),
                argThat { this.internalName == internalName },
            )
        ).thenReturn(
            NewBotResponse(name = externalName, clearTextPassword = "pw", authorSessionName = "as-AUTHOR1")
        )
        val bot = object : OverlordBot {
            override val descriptor = BotDescriptor(internalName, internalName)
        }
        val map = botMapService.getOrCreateBotMap(internalName, internalName)
        botComponent.startBot(map, bot)
        return map
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun buildEventB64(
        lockSession: String,
        serial: UShort,
        eventType: UByte,
        emergencyUnlocked: Boolean = false,
    ): String {
        val builder = FlatBufferBuilder(256)
        val lsOff = builder.createString(lockSession)
        val evIdOff = builder.createString("ev-1")
        val corrOff = builder.createString("corr-1")
        val metaOff = CommonMetadata.createCommonMetadata(builder, lsOff, serial, 1u, 0u, evIdOff, corrOff)
        val payloadOff = when (eventType) {
            EventType.EmergencyUnlock -> {
                EmergencyUnlock.startEmergencyUnlock(builder)
                EmergencyUnlock.endEmergencyUnlock(builder)
            }
            EventType.Lock -> {
                Lock.startLock(builder)
                Lock.endLock(builder)
            }
            EventType.PeriodicUpdate -> {
                PeriodicUpdate.createPeriodicUpdate(builder, !emergencyUnlocked, emergencyUnlocked, 120UL)
            }
            else -> error("Unsupported test eventType $eventType")
        }
        val eventOff = Event.createEvent(builder, metaOff, eventType, payloadOff)
        val sigOff = SignedEvent.createSignatureVector(builder, UByteArray(64))
        val rootOff = SignedEvent.createSignedEvent(builder, sigOff, eventOff)
        builder.finish(rootOff)
        return Base64.getEncoder().encodeToString(builder.sizedByteArray())
    }

    private fun convoContext(
        did: String = "did:plc:sub1",
        convoId: String = "convo-1",
        shareableToken: String? = "s-SUB1234",
        text: String = "!relock",
    ): ConversationContext {
        val sender = mock<ConvoDefsMessageViewSender> { on { this.did } doReturn did }
        val message = mock<ConvoDefsMessageView> {
            on { this.sender } doReturn sender
            on { this.text } doReturn text
        }
        val user = BSkyUser(did = did, convoId = convoId, shareableToken = shareableToken)
        return ConversationContext(bskyUser = user, convoId = convoId, currentMessage = message)
    }

    @Test
    fun `BotComponent tracks emergencyUnlocked across EmergencyUnlock, PeriodicUpdate, and Lock events and notifies once`() {
        registerBot("timer", "b-TIMER")

        val contract = contractService.save(
            Contract(
                name = "bc-1",
                externalContractName = "c-EXT1",
                botName = "b-TIMER",
                lockSessionToken = "ls-1",
                shareableToken = "s-TOK1",
                serialNumber = 101,
                state = ContractState.ACCEPTED,
            )
        )
        savedEngagements.add(
            BotEngagement(
                id = 10L,
                name = "tbr-10",
                botId = "timer",
                phase = EngagementPhase.ACTIVE,
                contractId = contract.id,
                contractSerialNumber = 101,
                shareableToken = "s-TOK1",
                subjectDid = "did:plc:sub1",
                convoId = "convo-sub1",
                data = TimerEngagementData(),
            )
        )

        val emergencyB64 = buildEventB64("ls-1", 101u, EventType.EmergencyUnlock)
        botComponent.handleWebSocketMessage(
            """{"type":"bot_event","botName":"b-TIMER","eventType":"EmergencyUnlock","lockSession":"ls-1","contractSerial":101,"rawSignedEventBase64":"$emergencyB64"}"""
        )

        assertTrue(savedContracts.first().emergencyUnlocked)
        verify(blueSkyService, times(1)).sendDm(eq("convo-sub1"), check { assertTrue(it.contains("!relock")) })

        // Second EmergencyUnlock event does not duplicate the DM
        botComponent.handleWebSocketMessage(
            """{"type":"bot_event","botName":"b-TIMER","eventType":"EmergencyUnlock","lockSession":"ls-1","contractSerial":101,"rawSignedEventBase64":"$emergencyB64"}"""
        )
        verify(blueSkyService, times(1)).sendDm(eq("convo-sub1"), any())

        // Lock event clears emergencyUnlocked
        val lockB64 = buildEventB64("ls-1", 101u, EventType.Lock)
        botComponent.handleWebSocketMessage(
            """{"type":"bot_event","botName":"b-TIMER","eventType":"Lock","lockSession":"ls-1","contractSerial":101,"rawSignedEventBase64":"$lockB64"}"""
        )
        assertFalse(savedContracts.first().emergencyUnlocked)

        // PeriodicUpdate reconciles emergencyUnlocked back to true, then false
        val periodicTrueB64 = buildEventB64("ls-1", 101u, EventType.PeriodicUpdate, emergencyUnlocked = true)
        botComponent.handleWebSocketMessage(
            """{"type":"bot_event","botName":"b-TIMER","eventType":"PeriodicUpdate","lockSession":"ls-1","contractSerial":101,"rawSignedEventBase64":"$periodicTrueB64"}"""
        )
        assertTrue(savedContracts.first().emergencyUnlocked)

        val periodicFalseB64 = buildEventB64("ls-1", 101u, EventType.PeriodicUpdate, emergencyUnlocked = false)
        botComponent.handleWebSocketMessage(
            """{"type":"bot_event","botName":"b-TIMER","eventType":"PeriodicUpdate","lockSession":"ls-1","contractSerial":101,"rawSignedEventBase64":"$periodicFalseB64"}"""
        )
        assertFalse(savedContracts.first().emergencyUnlocked)
    }

    @Test
    fun `EmergencyRelockService relocks emergency-unlocked contracts across multiple bots and deduplicates`() {
        registerBot("timer", "b-TIMER")
        registerBot("superBot", "b-SUPER")

        val timerContract = contractService.save(
            Contract(
                name = "bc-T1",
                externalContractName = "c-T1",
                botName = "b-TIMER",
                lockSessionToken = "ls-t1",
                shareableToken = "s-SUB1234",
                serialNumber = 201,
                nextCounter = 3,
                state = ContractState.ACCEPTED,
                emergencyUnlocked = true,
            )
        )
        val superContract = contractService.save(
            Contract(
                name = "bc-S1",
                externalContractName = "c-S1",
                botName = "b-SUPER",
                lockSessionToken = "ls-s1",
                shareableToken = "s-SUB1234",
                serialNumber = 202,
                nextCounter = 5,
                state = ContractState.CONFIRMED,
                emergencyUnlocked = true,
            )
        )
        // Ignored: not emergency-unlocked
        contractService.save(
            Contract(
                name = "bc-NORMAL",
                externalContractName = "c-NORMAL",
                botName = "b-TIMER",
                lockSessionToken = "ls-n1",
                shareableToken = "s-SUB1234",
                serialNumber = 203,
                state = ContractState.ACCEPTED,
                emergencyUnlocked = false,
            )
        )
        // Ignored: already released
        contractService.save(
            Contract(
                name = "bc-REL",
                externalContractName = "c-REL",
                botName = "b-TIMER",
                lockSessionToken = "ls-r1",
                shareableToken = "s-SUB1234",
                serialNumber = 204,
                state = ContractState.RELEASED,
                emergencyUnlocked = true,
            )
        )

        // Engagement also links to timerContract to verify deduplication
        savedEngagements.add(
            BotEngagement(
                id = 50L,
                name = "tbr-50",
                botId = "timer",
                phase = EngagementPhase.ACTIVE,
                contractId = timerContract.id,
                contractSerialNumber = 201,
                shareableToken = "s-SUB1234",
                subjectDid = "did:plc:sub1",
                convoId = "convo-1",
                data = TimerEngagementData(),
            )
        )

        whenever(coordinatorGateway.getContractBySession(eq("b-TIMER"), any(), eq("ls-t1"), eq(201))).thenReturn(
            BotContractInfo(name = "c-T1", nextCounter = 4, state = "ACCEPTED")
        )
        whenever(coordinatorGateway.getContractBySession(eq("b-SUPER"), any(), eq("ls-s1"), eq(202))).thenReturn(
            BotContractInfo(name = "c-S1", nextCounter = 6, state = "CONFIRMED")
        )
        whenever(coordinatorGateway.getContractBySession(eq("b-TIMER"), any(), eq("ls-n1"), eq(203))).thenReturn(
            BotContractInfo(name = "c-NORMAL", nextCounter = 2, state = "ACCEPTED")
        )

        val reply = emergencyRelockService.relockForUser(convoContext())

        assertTrue(reply.contains("2 contract(s)"), "Unexpected reply: $reply")
        verify(coordinatorGateway, times(1)).createCommand(
            botName = eq("b-TIMER"),
            botJwt = any(),
            contractName = eq("c-T1"),
            shareableToken = eq("s-SUB1234"),
            signedMessageBase64 = any(),
        )
        verify(coordinatorGateway, times(1)).createCommand(
            botName = eq("b-SUPER"),
            botJwt = any(),
            contractName = eq("c-S1"),
            shareableToken = eq("s-SUB1234"),
            signedMessageBase64 = any(),
        )
    }

    @Test
    fun `OverlordConversationHandler exposes !relock command and relock LLM intent`() {
        val mockRelockService = mock<EmergencyRelockService>()
        val handler = OverlordConversationHandler(
            blueSkyService = blueSkyService,
            bSkyUserService = bSkyUserService,
            contractService = contractService,
            botMapService = botMapService,
            botComponent = botComponent,
            emergencyRelockService = mockRelockService,
        )

        val ctx = convoContext()
        whenever(mockRelockService.relockForUser(ctx)).thenReturn("Sent relock command for 1 contract(s).")

        assertTrue("relock" in handler.commands)
        assertEquals("Sent relock command for 1 contract(s).", handler.handleCommand(ctx, "relock", emptyList()))

        val relockSpec = handler.intents.first { it.name == "relock" }
        val parsed = relockSpec.parseCall(emptyMap())
        val response = parsed.execute(ctx)
        assertEquals("Sent relock command for 1 contract(s).", response.text)
    }

    @Test
    fun `TimerBot and SuperBot skip release in reviewContracts while contract is emergencyUnlocked`() {
        val mockCtx = mock<BotContext> {
            on { externalName } doReturn "b-TEST"
            on { internalName } doReturn "test"
        }
        val mockBotComponent = mock<BotComponent> {
            on { contextFor(any<OverlordBot>()) } doReturn mockCtx
        }

        val expiredTimerContract = Contract(
            id = 301L,
            name = "bc-T301",
            botName = "b-TIMER",
            lockSessionToken = "ls-301",
            serialNumber = 301,
            state = ContractState.ACCEPTED,
            emergencyUnlocked = true,
        )
        savedEngagements.add(
            BotEngagement(
                id = 301L,
                name = "tbr-301",
                botId = "timer",
                phase = EngagementPhase.ACTIVE,
                contractId = 301L,
                contractSerialNumber = 301,
                subjectDid = "did:plc:sub1",
                convoId = "convo-1",
                data = TimerEngagementData(endsAt = fixedTime.minusMinutes(5)),
            )
        )

        val timerBot = TimerBot(
            engagementRepository = botEngagementRepository,
            timeSource = timeSource,
            botMapService = botMapService,
            botComponent = mockBotComponent,
        )
        timerBot.reviewContracts(mockCtx, listOf(expiredTimerContract))
        verify(mockCtx, never()).release(any())

        expiredTimerContract.emergencyUnlocked = false
        timerBot.reviewContracts(mockCtx, listOf(expiredTimerContract))
        verify(mockCtx, times(1)).release(expiredTimerContract)

        val completedSuperContract = Contract(
            id = 302L,
            name = "bc-S302",
            botName = "b-SUPER",
            lockSessionToken = "ls-302",
            serialNumber = 302,
            state = ContractState.ACCEPTED,
            emergencyUnlocked = true,
        )
        savedEngagements.add(
            BotEngagement(
                id = 302L,
                name = "sb-302",
                botId = "superBot",
                phase = EngagementPhase.ACTIVE,
                contractId = 302L,
                contractSerialNumber = 302,
                subjectDid = "did:plc:sub1",
                convoId = "convo-1",
                data = SuperBotEngagementData(),
            )
        )
        val mockObjectiveService = mock<ObjectiveService>()
        whenever(mockObjectiveService.process(any())).thenReturn(
            club.subjugated.overlord_exe.models.BSkyLikesObjectiveState(status = ObjectiveStatus.COMPLETE)
        )

        val superBot = SuperBot(
            engagementRepository = botEngagementRepository,
            timeSource = timeSource,
            botMapService = botMapService,
            botComponent = mockBotComponent,
            objectiveService = mockObjectiveService,
            bSkyUserService = bSkyUserService,
        )
        superBot.reviewContracts(mockCtx, listOf(completedSuperContract))
        verify(mockCtx, never()).release(completedSuperContract)

        completedSuperContract.emergencyUnlocked = false
        superBot.reviewContracts(mockCtx, listOf(completedSuperContract))
        verify(mockCtx, times(1)).release(completedSuperContract)
    }

    @Test
    fun `EmergencyRelockService force-resyncs stale CREATED contracts and falls back to live contracts when emergencyUnlocked is false`() {
        registerBot("timer", "b-TIMER")

        val staleContract = contractService.save(
            Contract(
                name = "bc-STALE",
                externalContractName = "c-STALE",
                botName = "b-TIMER",
                lockSessionToken = "ls-stale",
                shareableToken = "s-SUB1234",
                serialNumber = 401,
                nextCounter = 1,
                state = ContractState.CREATED,
                emergencyUnlocked = false,
            )
        )
        savedEngagements.add(
            BotEngagement(
                id = 401L,
                name = "tbr-401",
                botId = "timer",
                phase = EngagementPhase.ISSUED,
                contractId = staleContract.id,
                contractSerialNumber = 401,
                shareableToken = "s-SUB1234",
                subjectDid = "did:plc:sub1",
                convoId = "convo-1",
                data = TimerEngagementData(),
            )
        )

        whenever(coordinatorGateway.getContractBySession(eq("b-TIMER"), any(), eq("ls-stale"), eq(401))).thenReturn(
            BotContractInfo(name = "c-STALE", nextCounter = 3, state = "ACCEPTED")
        )

        val reply = emergencyRelockService.relockForUser(convoContext(shareableToken = null))

        assertTrue(reply.contains("1 contract(s)"), "Unexpected reply: $reply")
        assertEquals(ContractState.ACCEPTED, savedContracts.first().state)
        verify(coordinatorGateway, times(1)).createCommand(
            botName = eq("b-TIMER"),
            botJwt = any(),
            contractName = eq("c-STALE"),
            shareableToken = eq("s-SUB1234"),
            signedMessageBase64 = any(),
        )
    }

    @Test
    fun `BotComponent force-syncs non-live contract when EmergencyUnlock event arrives`() {
        registerBot("timer", "b-TIMER")

        contractService.save(
            Contract(
                name = "bc-SYNC",
                externalContractName = "c-SYNC",
                botName = "b-TIMER",
                lockSessionToken = "ls-sync",
                shareableToken = "s-SYNC1",
                serialNumber = 501,
                state = ContractState.CREATED,
            )
        )

        whenever(coordinatorGateway.getContractBySession(eq("b-TIMER"), any(), eq("ls-sync"), eq(501))).thenReturn(
            BotContractInfo(name = "c-SYNC", nextCounter = 2, state = "ACCEPTED")
        )

        val emergencyB64 = buildEventB64("ls-sync", 501u, EventType.EmergencyUnlock)
        botComponent.handleWebSocketMessage(
            """{"type":"bot_event","botName":"b-TIMER","eventType":"EmergencyUnlock","lockSession":"ls-sync","contractSerial":501,"rawSignedEventBase64":"$emergencyB64"}"""
        )

        assertEquals(ContractState.ACCEPTED, savedContracts.first().state)
        assertTrue(savedContracts.first().emergencyUnlocked)
    }
}
