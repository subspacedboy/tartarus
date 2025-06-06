package club.subjugated.overlord_exe.statemachines.bsky_crowd_time

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BSkyCrowdTimeStateMachineContextRepository : JpaRepository<BSkyCrowdTimeStateMachineContext, Long> {
    fun findByName(name : String) : BSkyCrowdTimeStateMachineContext
    fun findByStateMachineId( id : Long) : BSkyCrowdTimeStateMachineContext
}