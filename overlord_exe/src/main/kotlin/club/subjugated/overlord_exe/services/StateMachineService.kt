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
import java.util.concurrent.ConcurrentHashMap

@Service
class StateMachineService(
    private val stateMachineRepository: StateMachineRepository,
    private val timeSource: TimeSource,
    private val providers: List<ContextProvider<*, *>>,
    private val urlService: UrlService,
    private val logger: Logger = LoggerFactory.getLogger(StateMachineService::class.java),
) {
    val nameToProvider = ConcurrentHashMap<String, ContextProvider<*, *>>()
    private val formTypeToProvider = ConcurrentHashMap<String, ContextProvider<*, *>>()

    @PostConstruct
    fun buildRegistry() {
        providers.forEach { p ->
            nameToProvider[p.javaClass.name] = p
            p::class.qualifiedName?.let { nameToProvider[it] = p }
            if (p.objectiveId.isNotBlank()) {
                nameToProvider[p.objectiveId] = p
            }
            if (p.formTypeName.isNotBlank()) {
                formTypeToProvider[p.formTypeName] = p
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun resolveProvider(providerKey: String): ContextProvider<ContextForm, Context> {
        if (nameToProvider.isEmpty()) {
            buildRegistry()
        }
        return (nameToProvider[providerKey]
            ?: throw IllegalArgumentException("Unknown state machine provider: $providerKey"))
            as ContextProvider<ContextForm, Context>
    }

    @Suppress("UNCHECKED_CAST")
    fun resolveProviderByFormType(formType: String): ContextProvider<ContextForm, Context>? {
        if (formTypeToProvider.isEmpty()) {
            buildRegistry()
        }
        return formTypeToProvider[formType] as? ContextProvider<ContextForm, Context>
    }

    fun createNewStateMachine(
        ownerName: String,
        providerClassName: String,
        form: ContextForm,
        bskyUser: BSkyUser? = null,
    ): StateMachine {
        val provider = resolveProvider(providerClassName)

        val sm = StateMachine(
            ownedBy = ownerName,
            machineType = provider.javaClass.name,
            machineVersion = "1",
            providerClass = provider,
            infoResolveMethod = InfoResolveMethod.USER,
            bskyUser = bskyUser,
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

    private fun hydrate(stateMachine: StateMachine): StateMachine {
        val provider = resolveProvider(stateMachine.machineType)
        stateMachine.providerClass = provider
        stateMachine.context = provider.findByStateMachineId(stateMachine.id)
        return stateMachine
    }

    fun findById(id: Long): StateMachine {
        return hydrate(stateMachineRepository.findById(id).get())
    }

    fun findByName(name: String): StateMachine {
        return hydrate(stateMachineRepository.findByName(name))
    }

    fun findByOwnedBy(name: String): List<StateMachine> {
        val stateMachines = stateMachineRepository.findByOwnedBy(name)
        if (stateMachines.isEmpty()) {
            logger.warn("Zero state machines found for findByOwnedBy -> $name")
        }
        return stateMachines.map { hydrate(it) }
    }

    @Suppress("UNCHECKED_CAST")
    fun save(stateMachine: StateMachine): StateMachine {
        if (stateMachine.context != null) {
            val provider = stateMachine.providerClass ?: resolveProvider(stateMachine.machineType)
            (provider as ContextProvider<ContextForm, Context>).saveContext(stateMachine.context as Context)
        }

        stateMachine.updatedAt = timeSource.nowInUtc()
        return stateMachineRepository.save(stateMachine)
    }

    @Suppress("UNCHECKED_CAST")
    fun process(stateMachine: StateMachine) {
        val provider = (stateMachine.providerClass ?: resolveProvider(stateMachine.machineType))
            as ContextProvider<ContextForm, Context>
        provider.process(stateMachine, stateMachine.context!!)
    }
}
