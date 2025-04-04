package club.subjugated.tartarus_coordinator.services

import club.subjugated.tartarus_coordinator.api.messages.NewAuthorSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.UpdateKnownTokenMessage
import club.subjugated.tartarus_coordinator.models.AuthorSession
import club.subjugated.tartarus_coordinator.models.KnownToken
import club.subjugated.tartarus_coordinator.models.KnownTokenState
import club.subjugated.tartarus_coordinator.storage.AuthorSessionRepository
import club.subjugated.tartarus_coordinator.storage.KnownTokenRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class AuthorSessionService {
    @Autowired lateinit var authorSessionRepository: AuthorSessionRepository
    @Autowired lateinit var timeSource: TimeSource
    @Autowired lateinit var knownTokenRepository: KnownTokenRepository

    fun saveNewAuthorSession(newAuthorSessionMessage: NewAuthorSessionMessage): AuthorSession {
        val maybeSession =
            this.authorSessionRepository.findByPublicKey(newAuthorSessionMessage.publicKey!!)

        if (maybeSession != null) {
            // Ok, this author already had a session from some other time. Return that
            return maybeSession
        }

        val authorSession =
            AuthorSession(
                publicKey = newAuthorSessionMessage.publicKey,
                createdAt = timeSource.nowInUtc(),
                updatedAt = timeSource.nowInUtc(),
            )

        this.authorSessionRepository.save(authorSession)

        return authorSession
    }

    fun getSessionForBot(publicKey: String) : AuthorSession {
        return authorSessionRepository.findByPublicKey(publicKey)!!
    }

    fun saveAuthorSessionForBot(publicKey: String) : AuthorSession {
        val maybeSession =
            this.authorSessionRepository.findByPublicKey(publicKey)

        if (maybeSession != null) {
            // Ok, this author already had a session from some other time. Return that
            return maybeSession
        }

        val authorSession =
            AuthorSession(
                publicKey = publicKey,
                createdAt = timeSource.nowInUtc(),
                updatedAt = timeSource.nowInUtc(),
            )

        this.authorSessionRepository.save(authorSession)

        return authorSession
    }

    fun findByName(name: String): AuthorSession {
        return this.authorSessionRepository.findByName(name)
    }

    fun authorKnowsToken(authorSession: AuthorSession, shareableToken: String) : KnownToken {
        return knownTokenRepository.findByAuthorSessionIdAndShareableToken(
            authorSession.id,
            shareableToken,
        )
            ?: KnownToken(
                    state = KnownTokenState.CREATED,
                    authorSession = authorSession,
                    notes = "",
                    shareableToken = shareableToken,
                    createdAt = timeSource.nowInUtc(),
                )
                .let { knownTokenRepository.save(it) }
    }

    fun getKnownTokens(authorSession: AuthorSession): List<KnownToken> {
        return knownTokenRepository.findByAuthorSessionIdAndState(
            authorSession.id,
            KnownTokenState.CREATED,
        )
    }

    fun updateKnownToken(authorSessionName: String, updateKnownTokenMessage: UpdateKnownTokenMessage) : KnownToken {
        val authorSession = findByName(authorSessionName)
        val knownToken = knownTokenRepository.findByAuthorSessionIdAndName(authorSession.id, updateKnownTokenMessage.name)
        assert(authorSession == knownToken.authorSession)

        knownToken.notes = updateKnownTokenMessage.notes
        knownTokenRepository.save(knownToken)
        return knownToken
    }
}
