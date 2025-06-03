package club.subjugated.overlord_exe.statemachines

import club.subjugated.overlord_exe.models.StateMachine

interface ContextForm
interface Context

interface ContextProvider<F : ContextForm, C : Context> {
    fun createContext(stateMachineId : Long, form : F) : C
    fun findByStateMachineId(stateMachineId : Long) : C
    fun saveContext(ctx : C) : C
    fun getInitialState(): String
    fun process(stateMachine: StateMachine, ctx : C)
}