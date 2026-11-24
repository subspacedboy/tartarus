package club.subjugated.overlord_exe.machines

import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.models.ContractSession
import club.subjugated.overlord_exe.models.MachineStatus
import club.subjugated.overlord_exe.models.SessionMachine
import club.subjugated.overlord_exe.models.TimerMachineState
import club.subjugated.overlord_exe.runtime.BotContext
import club.subjugated.overlord_exe.util.TimeSource
import org.ocpsoft.prettytime.PrettyTime
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.temporal.ChronoUnit
import kotlin.random.Random

@Component
class TimerMachine(
    private val timeSource: TimeSource,
) : SessionMachineHandler<TimerMachineState> {

    override val machineType: String = "timer"
    override val displayName: String = "Timer"

    override fun createInitialState(session: ContractSession): TimerMachineState {
        return TimerMachineState()
    }

    override fun onAccept(
        ctx: BotContext,
        session: ContractSession,
        contract: Contract,
        machine: SessionMachine,
        state: TimerMachineState,
    ) {
        val acceptedAt = session.acceptedAt ?: timeSource.nowInUtc()
        state.endsAt = if (state.isRandom) {
            val minDuration = Duration.of(state.minDuration.toLong(), ChronoUnit.valueOf(state.minDurationUnit.uppercase()))
            val maxDuration = Duration.of(state.maxDuration.toLong(), ChronoUnit.valueOf(state.maxDurationUnit.uppercase()))
            val minMins = minDuration.toMinutes()
            val maxMins = maxOf(minMins, maxDuration.toMinutes())
            val randomMinutes = Random.nextLong(minMins, maxMins + 1)
            acceptedAt.plus(Duration.ofMinutes(randomMinutes))
        } else {
            val duration = Duration.of(state.maxDuration.toLong(), ChronoUnit.valueOf(state.maxDurationUnit.uppercase()))
            acceptedAt.plus(duration)
        }

        if (state.isRandom) {
            val relativeDuration = PrettyTime().format(state.endsAt)
            ctx.addMessage(contract, "Ends $relativeDuration")
        }
        machine.status = MachineStatus.RUNNING
        machine.state = state
    }

    override fun onTick(
        ctx: BotContext,
        session: ContractSession,
        contract: Contract,
        machine: SessionMachine,
        state: TimerMachineState,
    ) {
        if (machine.status != MachineStatus.RUNNING) return
        val endsAt = state.endsAt ?: return
        if (endsAt < timeSource.nowInUtc()) {
            state.completed = true
            machine.status = MachineStatus.SATISFIED
            machine.state = state
            ctx.addMessage(contract, "Time is up.")
        }
    }

    override fun onRelease(
        session: ContractSession,
        machine: SessionMachine,
        state: TimerMachineState,
    ) {
        state.completed = true
        machine.status = MachineStatus.SATISFIED
        machine.state = state
    }
}
