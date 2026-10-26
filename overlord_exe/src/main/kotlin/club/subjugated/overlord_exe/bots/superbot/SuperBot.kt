package club.subjugated.overlord_exe.bots.superbot

import club.subjugated.overlord_exe.bots.general.BotComponent
import club.subjugated.overlord_exe.bots.superbot.web.IntakeForm
import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.StateMachineState
import club.subjugated.overlord_exe.runtime.BotContext
import club.subjugated.overlord_exe.runtime.BotDescriptor
import club.subjugated.overlord_exe.runtime.OverlordBot
import club.subjugated.overlord_exe.services.BSkyUserService
import club.subjugated.overlord_exe.services.BotMapService
import club.subjugated.overlord_exe.services.StateMachineService
import club.subjugated.overlord_exe.util.TimeSource
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class SuperBot(
    private val superBotSessionRepository: SuperBotSessionRepository,
    private val timeSource: TimeSource,
    private val botMapService: BotMapService,
    private val botComponent: BotComponent,
    private val logger: Logger = LoggerFactory.getLogger(SuperBot::class.java),
    private val stateMachineService: StateMachineService,
    private val bSkyUserService: BSkyUserService,
) : OverlordBot {

    override val descriptor = BotDescriptor(
        internalName = "superBot",
        description = "OverLord SuperBot",
        reviewInterval = Duration.ofMinutes(1),
    )

    val botContext: BotContext
        get() = botComponent.contextFor(this)

    @PostConstruct
    fun start() {
        logger.info("Starting SuperBot")
        val botMap = getBotMap()
        botComponent.startBot(botMap, this)
    }

    fun getBotMap(): BotMap {
        return botMapService.getOrCreateBotMap(descriptor.internalName, descriptor.description)
    }

    fun createPlaceholder(did: String, convoId: String): String {
        val user = bSkyUserService.findOrCreateByDid(did)

        val session = SuperBotSession(
            contractSerialNumber = 0,
            state = SuperBotSessionState.CREATED,
            did = did,
            convoId = convoId,
            createdAt = timeSource.nowInUtc(),
            shareableToken = user.shareableToken,
        )
        superBotSessionRepository.save(session)

        return session.name
    }

    fun processContractForm(form: IntakeForm) {
        val session = findByName(form.name)

        if (session.state == SuperBotSessionState.ISSUED) {
            logger.warn("Attempted reprocessing ${form.name}")
            return
        }

        session.shareableToken = form.shareableToken
        session.state = SuperBotSessionState.ISSUED
        session.configuration.objectives = form.objectives

        val contract = botContext.issueContract(
            shareableToken = form.shareableToken,
            terms = "Superbot stuff",
            public = form.public,
        )

        session.contractSerialNumber = contract.serialNumber
        session.contractId = contract.id
        save(session)

        botContext.sendDm(
            convoId = session.convoId,
            message = "Contract issued",
        )
    }

    override fun reviewContracts(ctx: BotContext, contracts: List<Contract>) {
        logger.info("Going through superbot sessions")

        contracts.forEach { c ->
            val machines = stateMachineService.findByOwnedBy(c.name)
            machines.forEach { machine ->
                if (machine.state == StateMachineState.STARTED) {
                    stateMachineService.process(machine)
                    stateMachineService.save(machine)
                }
            }

            val allDone = machines.isNotEmpty() && machines.all { it.state == StateMachineState.COMPLETE }
            if (allDone) {
                ctx.release(c)
            }
        }
    }

    override fun reviewContracts(contracts: List<Contract>) {
        reviewContracts(botContext, contracts)
    }

    override fun onAccept(ctx: BotContext, contract: Contract) {
        val session = findByContractSerialNumber(contract.serialNumber.toLong())
        session.acceptedAt = timeSource.nowInUtc()
        session.contractId = contract.id
        superBotSessionRepository.save(session)

        val objective = session.chooseObjective()
        val bskyUser = bSkyUserService.findOrCreateByDid(session.did)

        val stateMachine = stateMachineService.createNewStateMachine(
            ownerName = contract.name,
            form = objective.first,
            providerClassName = objective.second,
            bskyUser = bskyUser,
        )

        stateMachineService.process(stateMachine)
        stateMachineService.save(stateMachine)
    }

    override fun handleAccept(contract: Contract) {
        onAccept(botContext, contract)
    }

    override fun onRelease(ctx: BotContext, contract: Contract) {
        val session = findByContractSerialNumber(contract.serialNumber.toLong())
        session.state = SuperBotSessionState.COMPLETE
        save(session)
    }

    override fun handleRelease(contract: Contract) {
        onRelease(botContext, contract)
    }

    fun findByName(name: String): SuperBotSession {
        return superBotSessionRepository.findByName(name)
    }

    fun save(session: SuperBotSession) {
        superBotSessionRepository.save(session)
    }

    fun findByContractSerialNumber(serialNumber: Long): SuperBotSession {
        return superBotSessionRepository.findByContractSerialNumber(serialNumber)
    }
}
