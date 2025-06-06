package club.subjugated.overlord_exe.statemachines.bsky_crowd_time.web

import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.StateMachineService
import club.subjugated.overlord_exe.statemachines.bsky_crowd_time.BSkyCrowdTimeState
import club.subjugated.overlord_exe.statemachines.bsky_crowd_time.BSkyCrowdTimeStateMachine
import club.subjugated.overlord_exe.statemachines.bsky_crowd_time.BSkyCrowdTimeStateMachineContext
import jakarta.ws.rs.core.MediaType
import org.ocpsoft.prettytime.PrettyTime
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping

@RequestMapping("/bsky_crowd_time")
@Controller
class BSkyCrowdTimeWebController(
    private val bSkyCrowdTimeStateMachine: BSkyCrowdTimeStateMachine,
    private val stateMachineService: StateMachineService,
    private val blueSkyService: BlueSkyService
) {
    @GetMapping("/status/{token}", produces = [MediaType.APPLICATION_JSON])
    fun getStatus(@PathVariable token: String, model: Model): String {
        val record1 = bSkyCrowdTimeStateMachine.findByName(token)

        val sm = stateMachineService.findById(record1.stateMachineId)
        val record = sm.context as BSkyCrowdTimeStateMachineContext

        val handle = blueSkyService.resolveDidToHandle(record.subjectDid)
        model.addAttribute("record", record)
        model.addAttribute("handle", handle)
        model.addAttribute("state", BSkyCrowdTimeState.valueOf(sm.currentState!!))

        if(record.endsAt != null) {
            val endsAtRelative = PrettyTime().format(record.endsAt)
            model.addAttribute("endsAtRelative", endsAtRelative)
        }
        return "bsky_crowd_time/status"
    }
}