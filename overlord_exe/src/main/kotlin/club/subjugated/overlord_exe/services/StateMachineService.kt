package club.subjugated.overlord_exe.services

import club.subjugated.overlord_exe.models.BSkyUser
import club.subjugated.overlord_exe.models.StateMachine
import club.subjugated.overlord_exe.models.StateMachineState
import club.subjugated.overlord_exe.statemachines.Context
import club.subjugated.overlord_exe.statemachines.ContextForm
import club.subjugated.overlord_exe.statemachines.ContextProvider
import club.subjugated.overlord_exe.statemachines.InfoResolveMethod
import club.subjugated.overlord_exe.storage.StateMachineRepository
import club.subjugated.overlord_exe.util.TimeSource
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class StateMachineService(
    private val stateMachineRepository: StateMachineRepository,
    private val timeSource: TimeSource,
    private val providers: List<ContextProvider<*, *>>,
    private val urlService: UrlService,
    private val logger: Logger = LoggerFactory.getLogger(StateMachineService::class.java)
) {
    val nameToProvider = HashMap<String, ContextProvider<*, *>>()

    @PostConstruct
    fun buildRegistry() {
        providers.forEach { p -> nameToProvider.put(p.javaClass.name, p) }
    }

    fun createNewStateMachine(ownerName : String, providerClassName: String, form: ContextForm, bskyUser : BSkyUser? = null) : StateMachine {
        @Suppress("UNCHECKED_CAST")
        val provider = nameToProvider.get(providerClassName) as ContextProvider<ContextForm, Context>

        val sm = StateMachine(
            ownedBy = ownerName,
            machineType = provider.javaClass.name,
            machineVersion = "1",
            providerClass = provider,
            infoResolveMethod = InfoResolveMethod.USER,
            bskyUser = bskyUser
        )
        sm.state = StateMachineState.STARTED
        sm.currentState = provider.getInitialState()
        sm.createdAt = timeSource.nowInUtc()

        save(sm)

        val ctx = provider.createContext(sm.id, form)
        sm.context = ctx

        save(sm)

        return sm
    }

    fun findById(id : Long) : StateMachine {
        val stateMachine = stateMachineRepository.findById(id).get()

        val provider = nameToProvider.get(stateMachine.machineType)
        stateMachine.providerClass = provider
        stateMachine.context = provider!!.findByStateMachineId(stateMachine.id)

        return stateMachine
    }

    fun findByName(name : String) : StateMachine {
        val stateMachine = stateMachineRepository.findByName(name)

        val provider = nameToProvider.get(stateMachine.machineType)
        stateMachine.providerClass = provider
        stateMachine.context = provider!!.findByStateMachineId(stateMachine.id)

        return stateMachine
    }

    fun findByOwnedBy(name : String) : List<StateMachine> {
        val stateMachines = stateMachineRepository.findByOwnedBy(name)

        if(stateMachines.isEmpty()) {
            logger.warn("Zero state machines found for findByOwnedBy -> $name")
        }

        stateMachines.forEach { sm ->
            val provider = nameToProvider.get(sm.machineType)
            sm.providerClass = provider
            sm.context = provider!!.findByStateMachineId(sm.id)
        }

        return stateMachines
    }

    fun save(stateMachine: StateMachine) : StateMachine {
        if(stateMachine.context != null) {
            val provider = stateMachine.providerClass
            (provider as ContextProvider<ContextForm, Context>).saveContext(stateMachine.context as Context)
        }

        stateMachine.updatedAt = timeSource.nowInUtc()
        return stateMachineRepository.save(stateMachine)
    }

    fun process(stateMachine: StateMachine) {
        if(stateMachine.providerClass == null) {
            throw IllegalStateException("Provider class cannot be null")
        }
        @Suppress("UNCHECKED_CAST")
        val provider = (stateMachine.providerClass as ContextProvider<ContextForm, Context>)
        provider.process(stateMachine, stateMachine.context!!)
    }
}