package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.api.messages.LockSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.NewLockSessionMessage
import club.subjugated.tartarus_coordinator.services.LockSessionService
import club.subjugated.tartarus_coordinator.util.getECPublicKeyFromByteArray
import jakarta.ws.rs.core.MediaType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/lock_sessions")
@Controller
class LockSessionController {
    @Autowired
    lateinit var lockSessionService: LockSessionService

    @PostMapping("/", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun saveLockSession(@RequestBody newLockSessionMessage: NewLockSessionMessage) : ResponseEntity<LockSessionMessage> {
        val decodedKeyBytes = Base64.getUrlDecoder().decode(newLockSessionMessage.publicKey)

        try {
            // Validate the public key
            getECPublicKeyFromByteArray(decodedKeyBytes)
        } catch (e : Exception) {
            return ResponseEntity.badRequest().build()
        }

        val session = lockSessionService.createLockSession(newLockSessionMessage)

        return ResponseEntity.ok(LockSessionMessage.fromLockSession(session))
    }
}