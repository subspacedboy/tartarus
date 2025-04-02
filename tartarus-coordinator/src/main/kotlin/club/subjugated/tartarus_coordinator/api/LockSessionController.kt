package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.api.messages.KnownTokenMessage
import club.subjugated.tartarus_coordinator.api.messages.LockSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.NewLockSessionMessage
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.LockSessionService
import club.subjugated.tartarus_coordinator.util.getECPublicKeyFromCompressedKeyByteArray
import jakarta.ws.rs.core.MediaType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/lock_sessions")
@Controller
class LockSessionController {
    @Autowired
    lateinit var lockSessionService: LockSessionService
    @Autowired
    lateinit var authorSessionService: AuthorSessionService

    @GetMapping("/mine/{someToken}", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun findMyLockSession(@PathVariable someToken : String) : ResponseEntity<LockSessionMessage>{
        val maybeSession = this.lockSessionService.findBySessionToken(someToken)
        return ResponseEntity.ok(LockSessionMessage.fromLockSession(maybeSession))
    }

    @GetMapping("/{someToken}", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun findLockSession(@AuthenticationPrincipal user: UserDetails, @PathVariable someToken : String) : ResponseEntity<LockSessionMessage>{
        val maybeSession = this.lockSessionService.findByShareableToken(someToken) ?: return ResponseEntity.notFound().build()

        val authorSession = this.authorSessionService.findByName(user.username)
        this.authorSessionService.authorKnowsToken(authorSession, someToken)

        return ResponseEntity.ok(LockSessionMessage.fromLockSession(maybeSession))
    }

    @GetMapping("/known", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun findKnownTokens(@AuthenticationPrincipal user: UserDetails) : ResponseEntity<List<KnownTokenMessage>>{
        val authorSession = this.authorSessionService.findByName(user.username)
        val knownTokens = this.authorSessionService.getKnownTokens(authorSession)

        return ResponseEntity.ok(knownTokens.map { KnownTokenMessage.fromKnownToken(it) })
    }

    @PostMapping("/", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun saveLockSession(@RequestBody newLockSessionMessage: NewLockSessionMessage) : ResponseEntity<LockSessionMessage> {
        val decodedKeyBytes = Base64.getUrlDecoder().decode(newLockSessionMessage.publicKey)

        try {
            // Validate the public key
            getECPublicKeyFromCompressedKeyByteArray(decodedKeyBytes)
        } catch (e : Exception) {
            return ResponseEntity.badRequest().build()
        }

        val session = lockSessionService.createLockSession(newLockSessionMessage)

        return ResponseEntity.ok(LockSessionMessage.fromLockSession(session))
    }
}