package club.subjugated.overlord_exe.statemachines.bsky_likes

import org.springframework.data.jpa.repository.JpaRepository

interface BSkyLikesStateMachineContextRepository : JpaRepository<BSkyLikesStateMachineContext, Long> {
    fun findByName(name : String) : BSkyLikesStateMachineContext
    fun findByStateMachineId( id : Long) : BSkyLikesStateMachineContext
}