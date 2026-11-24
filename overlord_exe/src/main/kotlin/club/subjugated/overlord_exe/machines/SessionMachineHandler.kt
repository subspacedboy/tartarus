package club.subjugated.overlord_exe.machines

import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractSession
import club.subjugated.overlord_exe.models.MachineState
import club.subjugated.overlord_exe.models.MachineStatus
import club.subjugated.overlord_exe.models.SessionMachine
import club.subjugated.overlord_exe.runtime.BotContext

interface SessionMachineHandler<S : MachineState> {
    val machineType: String
    val displayName: String
    val templateName: String? get() = null

    fun initialStatus(): MachineStatus = MachineStatus.RUNNING
    fun createInitialState(session: ContractSession): S
    fun createInfoForm(token: String): Any? = null
    fun applyInfoParams(machine: SessionMachine, state: S, params: Map<String, String>) {}

    fun onAccept(
        ctx: BotContext,
        session: ContractSession,
        contract: Contract,
        machine: SessionMachine,
        state: S,
    ) {}

    fun onTick(
        ctx: BotContext,
        session: ContractSession,
        contract: Contract,
        machine: SessionMachine,
        state: S,
    ) {}

    fun onRelease(
        session: ContractSession,
        machine: SessionMachine,
        state: S,
    ) {}
}
