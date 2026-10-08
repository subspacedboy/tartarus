package club.subjugated.tartarus_coordinator.api.admin

import club.subjugated.tartarus_coordinator.api.messages.LockSessionArgs
import club.subjugated.tartarus_coordinator.api.messages.LockSessionMessage
import club.subjugated.tartarus_coordinator.services.LockSessionService
import jakarta.ws.rs.core.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/lock_sessions")
@Controller
class AdminLockSessionController(private val lockSessionService: LockSessionService) {
    @GetMapping("/", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    fun getLiveContracts(
        @AuthenticationPrincipal admin: UserDetails,
        @RequestParam("states", required = false) statesParam: String?,
    ): ResponseEntity<List<LockSessionMessage>> {
        val lockSessions = lockSessionService.findAllForAdmin()

        return ResponseEntity.ok(
            lockSessions.map {
                LockSessionMessage.fromLockSession(
                    it,
                    null,
                    null,
                    listOf(LockSessionArgs.INCLUDE_SESSION_TOKEN),
                )
            }
        )
    }
}
