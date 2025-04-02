package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.api.messages.AuthorSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.NewAuthorSessionMessage
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import jakarta.ws.rs.core.MediaType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/author_sessions")
@Controller
class AuthorSessionController {
    @Autowired lateinit var authorSessionService: AuthorSessionService

    @PostMapping("/", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun saveAuthorSession(
        @RequestBody newAuthorSessionMessage: NewAuthorSessionMessage
    ): ResponseEntity<AuthorSessionMessage> {
        try {
            // Validate the public key
            newAuthorSessionMessage.validateOrThrow()
        } catch (e: Exception) {
            return ResponseEntity.badRequest().build()
        }

        val authorSession = this.authorSessionService.saveNewAuthorSession(newAuthorSessionMessage)

        return ResponseEntity.ok(AuthorSessionMessage.fromAuthorSession(authorSession))
    }
}
