package club.subjugated.overlord_exe.machines

import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractSession
import club.subjugated.overlord_exe.models.MachineStatus
import club.subjugated.overlord_exe.models.ProxyControlMachineState
import club.subjugated.overlord_exe.models.SessionMachine
import club.subjugated.overlord_exe.runtime.BotContext
import org.springframework.stereotype.Component

@Component
class ProxyControlMachine : SessionMachineHandler<ProxyControlMachineState> {

    override val machineType: String = "proxy_control"
    override val displayName: String = "Keyholder Proxy Control"

    override fun createInitialState(session: ContractSession): ProxyControlMachineState {
        return ProxyControlMachineState(
            keyholderDid = session.keyholderDid ?: "",
        )
    }

    override fun onAccept(
        ctx: BotContext,
        session: ContractSession,
        contract: Contract,
        machine: SessionMachine,
        state: ProxyControlMachineState,
    ) {
        machine.status = if (state.allowAutoRelease) MachineStatus.SATISFIED else MachineStatus.RUNNING
        if (state.keyholderConvoId.isNotBlank()) {
            ctx.sendDm(state.keyholderConvoId, "Contract accepted")
        }
    }

    override fun onRelease(
        session: ContractSession,
        machine: SessionMachine,
        state: ProxyControlMachineState,
    ) {
        machine.status = MachineStatus.SATISFIED
    }
}
