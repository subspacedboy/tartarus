package club.subjugated.overlord_exe.machines

import club.subjugated.overlord_exe.models.BSkyCrowdTimeMachineState
import club.subjugated.overlord_exe.models.BSkyLikesMachineState
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractSession
import club.subjugated.overlord_exe.models.MachineState
import club.subjugated.overlord_exe.models.MachineStatus
import club.subjugated.overlord_exe.models.ProxyControlMachineState
import club.subjugated.overlord_exe.models.ReleasePolicy
import club.subjugated.overlord_exe.models.SessionMachine
import club.subjugated.overlord_exe.models.SessionPhase
import club.subjugated.overlord_exe.models.TimerMachineState
import club.subjugated.overlord_exe.runtime.BotContext
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.ContractService
import club.subjugated.overlord_exe.services.TokenResolution
import club.subjugated.overlord_exe.services.UrlService
import club.subjugated.overlord_exe.services.UserProfileService
import club.subjugated.overlord_exe.storage.ContractSessionRepository
import club.subjugated.overlord_exe.storage.SessionMachineRepository
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.extractShareableToken
import club.subjugated.overlord_exe.util.formatDuration
import club.subjugated.overlord_exe.util.generateId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

data class TimerConfig(
    val isRandom: Boolean = false,
    val minDuration: Int = 1,
    val minUnit: String = "HOURS",
    val maxDuration: Int = 1,
    val maxUnit: String = "HOURS",
)

data class SessionIssueSpec(
    val shareableToken: String,
    val isPublic: Boolean = false,
    val releasePolicy: ReleasePolicy = ReleasePolicy.ALL,
    val machineTypes: List<String>,
    val timerConfig: TimerConfig? = null,
    val allowKeyholderAutoRelease: Boolean = false,
)

@Service
class SessionEngine(
    private val handlers: List<SessionMachineHandler<*>>,
    private val sessionRepository: ContractSessionRepository,
    private val machineRepository: SessionMachineRepository,
    private val contractService: ContractService,
    private val userProfileService: UserProfileService,
    private val urlService: UrlService,
    private val timeSource: TimeSource,
    private val blueSkyService: BlueSkyService? = null,
) {
    private val logger = LoggerFactory.getLogger(SessionEngine::class.java)

    private val handlersByType: Map<String, SessionMachineHandler<MachineState>> by lazy {
        @Suppress("UNCHECKED_CAST")
        handlers.associate { (it as SessionMachineHandler<MachineState>).machineType to it }
    }

    fun getHandler(machineType: String): SessionMachineHandler<MachineState> {
        return handlersByType[machineType]
            ?: throw IllegalArgumentException("Unknown machine type: $machineType")
    }

    fun saveSession(session: ContractSession): ContractSession {
        session.updatedAt = timeSource.nowInUtc()
        if (session.createdAt == null) session.createdAt = session.updatedAt
        return sessionRepository.save(session)
    }

    fun saveMachine(machine: SessionMachine): SessionMachine {
        machine.syncJsonBeforeSave()
        machine.updatedAt = timeSource.nowInUtc()
        if (machine.createdAt == null) machine.createdAt = machine.updatedAt
        return machineRepository.save(machine)
    }

    fun findByToken(token: String): ContractSession {
        return sessionRepository.findByToken(token)
            ?: throw IllegalStateException("Unknown session: $token")
    }

    fun findMachines(sessionId: Long): List<SessionMachine> {
        return machineRepository.findBySessionIdOrderByIdAsc(sessionId)
    }

    fun findByStatusToken(statusToken: String): SessionMachine? {
        return machineRepository.findByStatusToken(statusToken)
    }

    fun createDraftSession(
        subjectDid: String,
        convoId: String,
        keyholderDid: String? = null,
        preselectedMachines: List<String> = emptyList(),
        isPublic: Boolean? = null,
        releasePolicy: ReleasePolicy = ReleasePolicy.ALL,
        explicitShareableToken: String? = null,
    ): ContractSession {
        val profile = userProfileService.findOrCreateByDid(subjectDid)
        val resolvedToken = explicitShareableToken?.takeIf { it.isNotBlank() }
            ?: when (val res = userProfileService.resolveTokenForUser(subjectDid)) {
                is TokenResolution.Single -> res.token.token
                else -> ""
            }

        val effectivePublic = isPublic ?: profile.preferences.defaultIsPublic ?: false
        val session = saveSession(
            ContractSession(
                token = generateId("ses-"),
                botId = "overlord",
                phase = SessionPhase.CREATED,
                releasePolicy = releasePolicy,
                isPublic = effectivePublic,
                shareableToken = resolvedToken,
                subjectDid = subjectDid,
                convoId = convoId,
                keyholderDid = keyholderDid,
                createdAt = timeSource.nowInUtc(),
                updatedAt = timeSource.nowInUtc(),
            )
        )

        if (preselectedMachines.isNotEmpty()) {
            attachMachines(session, preselectedMachines, timerConfig = null, allowAutoRelease = false)
        }
        return session
    }

    fun issueSession(
        ctx: BotContext,
        session: ContractSession,
        spec: SessionIssueSpec,
    ): ContractSession {
        if (session.phase != SessionPhase.CREATED) {
            logger.warn("Attempted reprocessing session ${session.token} in phase ${session.phase}")
            return session
        }

        val cleanToken = extractShareableToken(spec.shareableToken)
        require(cleanToken.isNotBlank()) { "Shareable token is required to issue a contract" }

        // Learn token & preference on the user's background profile
        userProfileService.recordToken(
            did = session.subjectDid,
            rawToken = cleanToken,
            markUsed = true,
        )
        userProfileService.updateDefaultPublic(session.subjectDid, spec.isPublic)

        session.shareableToken = cleanToken
        session.isPublic = spec.isPublic
        session.releasePolicy = spec.releasePolicy

        // Replace any draft machines with the submitted configuration
        val existingMachines = findMachines(session.id)
        if (existingMachines.isNotEmpty()) {
            machineRepository.deleteAll(existingMachines)
        }
        val machines = attachMachines(
            session = session,
            machineTypes = spec.machineTypes,
            timerConfig = spec.timerConfig,
            allowAutoRelease = spec.allowKeyholderAutoRelease,
        )

        val terms = buildTerms(session, machines, spec)
        session.terms = terms
        session.phase = SessionPhase.ISSUED

        val contract = ctx.issueContract(
            shareableToken = cleanToken,
            terms = terms,
            public = spec.isPublic,
        )
        session.contractSerialNumber = contract.serialNumber
        session.contractId = contract.id
        saveSession(session)

        if (session.convoId.isNotBlank()) {
            val khDid = session.keyholderDid
            if (!khDid.isNullOrBlank()) {
                val khHandle = userProfileService.findByDid(khDid)?.handle?.takeIf { it.isNotBlank() } ?: khDid
                ctx.sendDm(session.convoId, "You have been issued a contract by $khHandle")
            } else {
                ctx.sendDm(session.convoId, "Contract issued")
            }
        }
        session.keyholderDid?.let { khDid ->
            val khConvo = userProfileService.findByDid(khDid)?.convoId
            if (!khConvo.isNullOrBlank() && khConvo != session.convoId) {
                ctx.sendDm(khConvo, "Contract issued")
            }
        }

        return session
    }

    private fun attachMachines(
        session: ContractSession,
        machineTypes: List<String>,
        timerConfig: TimerConfig?,
        allowAutoRelease: Boolean,
    ): List<SessionMachine> {
        val now = timeSource.nowInUtc()
        return machineTypes.distinct().map { type ->
            val handler = getHandler(type)
            val initialState = handler.createInitialState(session)
            when (initialState) {
                is TimerMachineState -> if (timerConfig != null) {
                    initialState.isRandom = timerConfig.isRandom
                    initialState.minDuration = timerConfig.minDuration
                    initialState.minDurationUnit = timerConfig.minUnit
                    initialState.maxDuration = timerConfig.maxDuration
                    initialState.maxDurationUnit = timerConfig.maxUnit
                }
                is ProxyControlMachineState -> {
                    val kh = session.keyholderDid?.let { userProfileService.findByDid(it) }
                    val sub = userProfileService.findByDid(session.subjectDid)
                    initialState.keyholderDid = session.keyholderDid ?: ""
                    initialState.keyholderHandle = kh?.handle ?: ""
                    initialState.keyholderConvoId = kh?.convoId ?: ""
                    initialState.subHandle = sub?.handle ?: ""
                    initialState.allowAutoRelease = allowAutoRelease
                }
                else -> {}
            }

            val statusToken = (initialState as? BSkyCrowdTimeMachineState)?.name
            saveMachine(
                SessionMachine(
                    sessionId = session.id,
                    machineType = type,
                    status = handler.initialStatus(),
                    statusToken = statusToken,
                    createdAt = now,
                    updatedAt = now,
                    state = initialState,
                )
            )
        }
    }

    private fun buildTerms(
        session: ContractSession,
        machines: List<SessionMachine>,
        spec: SessionIssueSpec,
    ): String {
        val parts = machines.map { m ->
            when (val s = m.state) {
                is TimerMachineState -> if (s.isRandom) {
                    "Timer: random duration"
                } else {
                    "Timer: ${formatDuration(s.maxDuration.toLong(), s.maxDurationUnit)}"
                }
                is ProxyControlMachineState -> "Proxy: ${s.keyholderHandle.ifBlank { s.keyholderDid }}"
                is BSkyLikesMachineState -> "Bluesky Likes"
                is BSkyCrowdTimeMachineState -> "Bluesky Crowd Time"
            }
        }
        val prefix = if (spec.releasePolicy == ReleasePolicy.MANUAL) "Keyholder controlled" else "Overlord"
        return "$prefix (${parts.joinToString(" + ")})".take(250)
    }

    fun onAccept(ctx: BotContext, contract: Contract) {
        val session = findSessionForContract(contract) ?: return
        session.acceptedAt = timeSource.nowInUtc()
        session.contractId = contract.id
        session.phase = SessionPhase.ACTIVE
        saveSession(session)

        val machines = findMachines(session.id)
        for (machine in machines) {
            val handler = getHandler(machine.machineType)
            handler.onAccept(ctx, session, contract, machine, machine.state)
            if (machine.status == MachineStatus.NEEDS_INFO) {
                dispatchInfoRequest(ctx, session, machine)
            } else {
                saveMachine(machine)
            }
        }
    }

    fun reviewContracts(ctx: BotContext, contracts: List<Contract>) {
        for (contract in contracts) {
            val session = findSessionForContract(contract) ?: continue
            if (session.phase != SessionPhase.ACTIVE) continue

            val machines = findMachines(session.id)
            for (machine in machines) {
                when (machine.status) {
                    MachineStatus.NEEDS_INFO -> dispatchInfoRequest(ctx, session, machine)
                    MachineStatus.RUNNING -> {
                        val handler = getHandler(machine.machineType)
                        handler.onTick(ctx, session, contract, machine, machine.state)
                        saveMachine(machine)
                    }
                    else -> {}
                }
            }

            if (shouldAutoRelease(session, machines, contract)) {
                ctx.release(contract)
            }
        }
    }

    internal fun shouldAutoRelease(
        session: ContractSession,
        machines: List<SessionMachine>,
        contract: Contract,
    ): Boolean {
        if (contract.emergencyUnlocked || machines.isEmpty()) return false
        return when (session.releasePolicy) {
            ReleasePolicy.MANUAL -> false
            ReleasePolicy.ALL -> machines.all { it.status == MachineStatus.SATISFIED }
            ReleasePolicy.ANY -> machines.any { it.status == MachineStatus.SATISFIED }
        }
    }

    fun onRelease(ctx: BotContext, contract: Contract) {
        val session = findSessionForContract(contract) ?: return
        session.phase = SessionPhase.COMPLETE
        session.completedAt = timeSource.nowInUtc()
        saveSession(session)

        findMachines(session.id).forEach { machine ->
            val handler = getHandler(machine.machineType)
            handler.onRelease(session, machine, machine.state)
            saveMachine(machine)
        }
        userProfileService.recordSessionOutcome(session.subjectDid, completed = true)
    }

    fun onAbort(ctx: BotContext, contract: Contract) {
        val session = findSessionForContract(contract) ?: return
        session.phase = SessionPhase.ABORTED
        session.completedAt = timeSource.nowInUtc()
        saveSession(session)
        userProfileService.recordSessionOutcome(session.subjectDid, completed = false, aborted = true)
    }

    private fun dispatchInfoRequest(ctx: BotContext, session: ContractSession, machine: SessionMachine) {
        val token = generateId("ir-")
        machine.infoToken = token
        machine.status = MachineStatus.WAITING_FORM
        saveMachine(machine)
        if (session.convoId.isNotBlank()) {
            val url = urlService.generateUrl("info/$token")
            ctx.sendDm(session.convoId, url)
        }
    }

    fun getInfoForm(infoToken: String): Pair<String, Any>? {
        val machine = machineRepository.findByInfoToken(infoToken) ?: return null
        if (machine.status != MachineStatus.WAITING_FORM) return null
        val handler = getHandler(machine.machineType)
        val template = handler.templateName ?: return null
        val form = handler.createInfoForm(infoToken) ?: return null
        return template to form
    }

    fun submitInfoForm(params: Map<String, String>): SessionMachine {
        val token = params["name"] ?: throw IllegalArgumentException("Missing info token 'name'")
        val machine = machineRepository.findByInfoToken(token)
            ?: throw IllegalStateException("Unknown or already submitted info token: $token")
        val handler = getHandler(machine.machineType)
        handler.applyInfoParams(machine, machine.state, params)
        machine.infoToken = null
        return saveMachine(machine)
    }

    fun findSessionForContract(contract: Contract): ContractSession? {
        if (contract.id != 0L) {
            sessionRepository.findByContractId(contract.id)?.let { return it }
        }
        return sessionRepository.findByBotIdAndContractSerialNumber("overlord", contract.serialNumber)
    }

    fun findActiveContractsForPair(keyholderDid: String, subjectDid: String): List<Contract> {
        val activeSessions = sessionRepository.findByBotIdAndKeyholderDidAndSubjectDidAndPhaseIn(
            botId = "overlord",
            keyholderDid = keyholderDid,
            subjectDid = subjectDid,
            phases = listOf(SessionPhase.ACTIVE),
        )
        if (activeSessions.isEmpty()) {
            throw IllegalStateException("No active contract found for this pair")
        }
        return activeSessions.map { session ->
            contractService.getContract("overlord", session.contractSerialNumber).let { c ->
                c
            }
        }
    }

    fun lockPair(ctx: BotContext, keyholderDid: String, subjectDid: String): Result<Unit> = runCatching {
        val activeSessions = sessionRepository.findByBotIdAndKeyholderDidAndSubjectDidAndPhaseIn(
            botId = "overlord",
            keyholderDid = keyholderDid,
            subjectDid = subjectDid,
            phases = listOf(SessionPhase.ACTIVE),
        )
        if (activeSessions.isEmpty()) throw IllegalStateException("No active contract found for this pair")
        activeSessions.forEach { session ->
            val contract = contractService.getContract(ctx.externalName, session.contractSerialNumber)
            ctx.lock(contract)
        }
    }

    fun unlockPair(ctx: BotContext, keyholderDid: String, subjectDid: String): Result<Unit> = runCatching {
        val activeSessions = sessionRepository.findByBotIdAndKeyholderDidAndSubjectDidAndPhaseIn(
            botId = "overlord",
            keyholderDid = keyholderDid,
            subjectDid = subjectDid,
            phases = listOf(SessionPhase.ACTIVE),
        )
        if (activeSessions.isEmpty()) throw IllegalStateException("No active contract found for this pair")
        activeSessions.forEach { session ->
            val contract = contractService.getContract(ctx.externalName, session.contractSerialNumber)
            ctx.unlock(contract)
        }
    }

    fun releasePair(ctx: BotContext, keyholderDid: String, subjectDid: String): Result<Unit> = runCatching {
        val activeSessions = sessionRepository.findByBotIdAndKeyholderDidAndSubjectDidAndPhaseIn(
            botId = "overlord",
            keyholderDid = keyholderDid,
            subjectDid = subjectDid,
            phases = listOf(SessionPhase.ACTIVE),
        )
        if (activeSessions.isEmpty()) throw IllegalStateException("No active contract found for this pair")
        activeSessions.forEach { session ->
            val contract = contractService.getContract(ctx.externalName, session.contractSerialNumber)
            ctx.release(contract)
        }
    }
}
