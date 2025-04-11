package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.api.messages.KnownTokenMessage
import club.subjugated.tartarus_coordinator.api.messages.LockSessionArgs
import club.subjugated.tartarus_coordinator.api.messages.LockSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.NewLockSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.UpdateKnownTokenMessage
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.LockSessionService
import club.subjugated.tartarus_coordinator.services.LockUserSessionService
import club.subjugated.tartarus_coordinator.util.getECPublicKeyFromCompressedKeyByteArray
import jakarta.ws.rs.core.MediaType
import org.bouncycastle.crypto.CryptoException
import java.util.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/lock_sessions")
@Controller
class LockSessionController {
    @Autowired lateinit var lockSessionService: LockSessionService
    @Autowired lateinit var lockUserSessionService: LockUserSessionService
    @Autowired lateinit var authorSessionService: AuthorSessionService

    @GetMapping("/mine", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun findMyLockSession(@AuthenticationPrincipal user: UserDetails): ResponseEntity<LockSessionMessage> {
        val lockUserSession = lockUserSessionService.findByName(user.username)
        val maybeSession = this.lockSessionService.findBySessionToken(lockUserSession.lockSession.sessionToken!!)
        return ResponseEntity.ok(LockSessionMessage.fromLockSession(maybeSession!!, null, null,  listOf()))
    }

    @GetMapping("/{someToken}", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun findLockSession(
        @AuthenticationPrincipal author: UserDetails,
        @PathVariable someToken: String,
    ): ResponseEntity<LockSessionMessage> {
        val maybeSession =
            this.lockSessionService.findByShareableToken(someToken)
                ?: return ResponseEntity.notFound().build()

        val authorSession = this.authorSessionService.findByName(author.username)
        val knownToken = this.authorSessionService.authorKnowsToken(authorSession, someToken)

        val args = if(someToken != maybeSession.totalControlToken) {
            // This is the general shareable token, so make sure it doesn't include
            // either lock state or the total control token.
            listOf(LockSessionArgs.SUPPRESS_TC_TOKEN, LockSessionArgs.SUPPRESS_LOCK_STATE, LockSessionArgs.SUPPRESS_AVAILABLE_FOR_CONTRACT)
        } else {
            // This was the total control token. But we still don't include lock state. We do however
            // include if it's available for contract.
            listOf(LockSessionArgs.SUPPRESS_LOCK_STATE)
        }
        return ResponseEntity.ok(LockSessionMessage.fromLockSession(maybeSession, null, knownToken, args))
    }

    @GetMapping("/known", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun findKnownTokens(
        @AuthenticationPrincipal authorUser: UserDetails
    ): ResponseEntity<List<KnownTokenMessage>> {
        val authorSession = this.authorSessionService.findByName(authorUser.username)
        val knownTokens = this.authorSessionService.getKnownTokens(authorSession)

        return ResponseEntity.ok(knownTokens.map { KnownTokenMessage.fromKnownToken(it) })
    }

    @PutMapping("/known/{tokenName}", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun saveKnownTokenUpdate(
        @AuthenticationPrincipal authorUser: UserDetails,
        @RequestBody updateKnownTokenMessage: UpdateKnownTokenMessage
    ): ResponseEntity<Void> {
        val knownToken = this.authorSessionService.updateKnownToken(authorUser.username, updateKnownTokenMessage)
        return ResponseEntity.ok(null)
    }
}
