package club.subjugated.overlord_exe.services

import club.subjugated.overlord_exe.models.StateMachine
import club.subjugated.overlord_exe.models.StateMachineState
import club.subjugated.overlord_exe.statemachines.Context
import club.subjugated.overlord_exe.statemachines.ContextForm
import club.subjugated.overlord_exe.statemachines.ContextProvider
import club.subjugated.overlord_exe.statemachines.ContextProviderRegistry
import club.subjugated.overlord_exe.storage.StateMachineRepository
import club.subjugated.overlord_exe.util.TimeSource
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import javax.swing.plaf.nimbus.State
import kotlin.reflect.KClass

@Service
class StateMachineService(
    private val stateMachineRepository: StateMachineRepository,
    private val timeSource: TimeSource,
    private val providers: List<ContextProvider<*, *>>
) {
    val nameToProvider = HashMap<String, ContextProvider<*, *>>()

    @PostConstruct
    fun buildRegistry() {
        providers.forEach { p -> nameToProvider.put(p.javaClass.name, p) }
    }

    fun <F : ContextForm, C : Context> createNewStateMachine(ownerName : String, providerClassName: String, form: F) : StateMachine {
        @Suppress("UNCHECKED_CAST")
        val provider = nameToProvider.get(providerClassName) as ContextProvider<F, C>

        val sm = StateMachine(
            ownedBy = ownerName,
            machineType = provider.javaClass.name,
            machineVersion = "1",
            providerClass = provider
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

    fun findByName(name : String) : StateMachine {
        val stateMachine = stateMachineRepository.findByName(name)

        val provider = nameToProvider.get(stateMachine.machineType)
        stateMachine.context = provider!!.findByStateMachineId(stateMachine.id)

        return stateMachine
    }

    fun findByOwnedBy(name : String) : List<StateMachine> {
        val stateMachines = stateMachineRepository.findByOwnedBy(name)

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