package club.subjugated.overlord_exe.services

import club.subjugated.overlord_exe.models.StateMachine
import club.subjugated.overlord_exe.storage.StateMachineRepository
import org.springframework.stereotype.Service

@Service
class StateMachineService(
    private val stateMachineRepository: StateMachineRepository
) {
    fun findByName(name : String) : StateMachine {
        return stateMachineRepository.findByName(name)
    }
}