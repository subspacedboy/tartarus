package club.subjugated.tartarus_coordinator.services

import club.subjugated.tartarus_coordinator.api.messages.NewAuthorSessionMessage
import club.subjugated.tartarus_coordinator.models.AuthorSession
import club.subjugated.tartarus_coordinator.storage.AuthorSessionRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class AuthorSessionService {
    @Autowired
    lateinit var authorSessionRepository: AuthorSessionRepository
    @Autowired
    lateinit var timeSource: TimeSource
    fun saveNewAuthorSession(newAuthorSessionMessage: NewAuthorSessionMessage) : AuthorSession {
        val maybeSession = this.authorSessionRepository.findByPublicKey(newAuthorSessionMessage.publicKey!!)

        if(maybeSession != null) {
            // Ok, this author already had a session from some other time. Return that
            return maybeSession
        }

        val authorSession = AuthorSession(
            publicKey = newAuthorSessionMessage.publicKey,
            createdAt = timeSource.nowInUtc(),
            updatedAt = timeSource.nowInUtc()
        )

        this.authorSessionRepository.save(authorSession)

        return authorSession
    }

    fun findByName(name : String) : AuthorSession {
        return this.authorSessionRepository.findByName(name)
    }
}