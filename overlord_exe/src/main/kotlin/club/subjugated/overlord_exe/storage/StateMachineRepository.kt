package club.subjugated.overlord_exe.storage

import club.subjugated.overlord_exe.models.StateMachine
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StateMachineRepository : JpaRepository<StateMachine, Long> {
    fun findByName(name : String) : StateMachine
    fun findByOwnedBy(name : String): List<StateMachine>
}