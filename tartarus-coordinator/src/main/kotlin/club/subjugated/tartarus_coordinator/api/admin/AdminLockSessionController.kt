package club.subjugated.tartarus_coordinator.api.admin

import club.subjugated.tartarus_coordinator.api.messages.ContractMessage
import club.subjugated.tartarus_coordinator.api.messages.LockSessionArgs
import club.subjugated.tartarus_coordinator.api.messages.LockSessionMessage
import club.subjugated.tartarus_coordinator.services.LockSessionService
import jakarta.ws.rs.core.MediaType
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/admin/lock_sessions")
@Controller
class AdminLockSessionController(private val lockSessionService: LockSessionService) {
    @GetMapping("/", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    fun getLockSessions(
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

    @GetMapping("/{sessionToken}", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    fun getLockSessionByToken(
        @AuthenticationPrincipal admin: UserDetails,
        @PathVariable sessionToken: String,
    ): ResponseEntity<LockSessionMessage> {
        val lockSession =
            lockSessionService.findByTokenOrName(sessionToken)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Lock session not found")

        return ResponseEntity.ok(
            LockSessionMessage.fromLockSession(
                lockSession,
                null,
                null,
                listOf(LockSessionArgs.INCLUDE_SESSION_TOKEN),
            )
        )
    }

    @GetMapping("/{sessionToken}/contracts", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    fun getContractsForLockSession(
        @AuthenticationPrincipal admin: UserDetails,
        @PathVariable sessionToken: String,
    ): ResponseEntity<List<ContractMessage>> {
        val lockSession =
            lockSessionService.findByTokenOrName(sessionToken)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Lock session not found")

        val contracts = lockSessionService.findContractsForLockSession(lockSession)

        return ResponseEntity.ok(
            contracts.map {
                it.lockState = lockSession.isLocked
                it.emergencyUnlocked = lockSession.emergencyUnlocked
                it.lockLastUpdate = lockSession.updatedAt
                ContractMessage.fromContract(it)
            }
        )
    }
}
