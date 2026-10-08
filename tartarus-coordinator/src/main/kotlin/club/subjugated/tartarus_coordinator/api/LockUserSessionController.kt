package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.api.messages.LockUserSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.NewLockUserSessionMessage
import club.subjugated.tartarus_coordinator.services.LockSessionService
import club.subjugated.tartarus_coordinator.services.LockUserSessionService
import jakarta.ws.rs.core.MediaType
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/lock_user_sessions")
@Controller
class LockUserSessionController(
    private var lockUserSessionService: LockUserSessionService,
    private var lockSessionService: LockSessionService,
) {
    @PostMapping("/", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun createLockUserSession(
        @RequestBody newLockUserSessionMessage: NewLockUserSessionMessage
    ): ResponseEntity<LockUserSessionMessage> {
        val lockSession =
            lockSessionService.findBySessionToken(newLockUserSessionMessage.sessionToken!!)
        val lockUserSession =
            lockUserSessionService.authenticateViaCryptogram(
                lockSession!!,
                newLockUserSessionMessage.cipher!!,
                newLockUserSessionMessage.nonce!!,
                newLockUserSessionMessage.sessionToken!!,
                newLockUserSessionMessage.lockUserSessionPublicKey!!,
            )

        if (lockUserSession == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return ResponseEntity.ok(LockUserSessionMessage.fromLockUserSession(lockUserSession))
    }
}
