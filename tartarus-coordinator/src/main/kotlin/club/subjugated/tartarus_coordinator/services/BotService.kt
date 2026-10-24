package club.subjugated.tartarus_coordinator.services

import club.subjugated.tartarus_coordinator.api.messages.NewBotMessage
import club.subjugated.tartarus_coordinator.api.messages.SyncBotsRequest
import club.subjugated.tartarus_coordinator.api.messages.UpdateBotMessage
import club.subjugated.tartarus_coordinator.models.AuthorSession
import club.subjugated.tartarus_coordinator.models.Bot
import club.subjugated.tartarus_coordinator.models.BotState
import club.subjugated.tartarus_coordinator.storage.BotRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import club.subjugated.tartarus_coordinator.util.getECPublicKeyFromCompressedKeyByteArray
import java.util.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class BotService {
    @Autowired lateinit var botRepository: BotRepository

    @Autowired lateinit var timeSource: TimeSource

    @Suppress("TooGenericExceptionCaught")
    private fun validatePublicKey(publicKey: String) {
        try {
            getECPublicKeyFromCompressedKeyByteArray(Base64.getDecoder().decode(publicKey))
        } catch (ex: Exception) {
            throw IllegalArgumentException("Public key couldn't be parsed", ex)
        }
    }

    fun createNewBot(authorSession: AuthorSession, newBotMessage: NewBotMessage): Bot {
        validatePublicKey(newBotMessage.publicKey)

        val existingByInternalName =
            if (!newBotMessage.internalName.isNullOrBlank()) {
                botRepository.findByAuthorSessionIdAndInternalName(
                    authorSession.id,
                    newBotMessage.internalName,
                )
            } else {
                null
            }
        val existingByPubKey = botRepository.findByPublicKey(newBotMessage.publicKey)

        val botToSave =
            when {
                existingByInternalName != null -> {
                    existingByInternalName.apply {
                        publicKey = newBotMessage.publicKey
                        description = newBotMessage.description
                        keyVersion = newBotMessage.keyVersion
                        updatedAt = timeSource.nowInUtc()
                    }
                }
                existingByPubKey != null -> {
                    if (
                        existingByPubKey.authorSession != null &&
                            existingByPubKey.authorSession!!.id != authorSession.id
                    ) {
                        throw ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Bot public key is already registered to another author",
                        )
                    }
                    existingByPubKey.apply {
                        this.authorSession = authorSession
                        if (!newBotMessage.internalName.isNullOrBlank()) {
                            internalName = newBotMessage.internalName
                        }
                        description = newBotMessage.description
                        keyVersion = newBotMessage.keyVersion
                        updatedAt = timeSource.nowInUtc()
                    }
                }
                else ->
                    Bot(
                        state = BotState.ACTIVE,
                        publicKey = newBotMessage.publicKey,
                        description = newBotMessage.description,
                        authorSession = authorSession,
                        internalName = newBotMessage.internalName,
                        keyVersion = newBotMessage.keyVersion,
                        createdAt = timeSource.nowInUtc(),
                        updatedAt = timeSource.nowInUtc(),
                    )
            }

        return botRepository.save(botToSave)
    }

    fun syncBots(authorSession: AuthorSession, request: SyncBotsRequest): List<Bot> {
        return request.bots.map { createNewBot(authorSession, it) }
    }

    fun updateBot(
        authorSession: AuthorSession,
        botName: String,
        updateBotMessage: UpdateBotMessage,
    ): Bot {
        val bot = botRepository.findByName(botName)
        if (bot.authorSession?.id != authorSession.id) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Bot does not belong to this author",
            )
        }

        if (updateBotMessage.description != null) {
            bot.description = updateBotMessage.description
        }
        if (updateBotMessage.state != null) {
            bot.state = BotState.valueOf(updateBotMessage.state)
        }
        if (updateBotMessage.publicKey != null) {
            validatePublicKey(updateBotMessage.publicKey)
            bot.publicKey = updateBotMessage.publicKey
        }
        if (updateBotMessage.keyVersion != null) {
            bot.keyVersion = updateBotMessage.keyVersion
        }
        bot.updatedAt = timeSource.nowInUtc()
        return botRepository.save(bot)
    }

    fun getBotsForAuthor(authorSession: AuthorSession): List<Bot> {
        return botRepository.findByAuthorSessionIdOrderByCreatedAtDesc(authorSession.id)
    }

    fun getAll(): List<Bot> {
        return botRepository.findByStateOrderByCreatedAtDesc(BotState.ACTIVE)
    }

    fun getByName(name: String): Bot {
        return botRepository.findByName(name)
    }

    fun findByPublicKey(publicKey: String): Bot? {
        return botRepository.findByPublicKey(publicKey)
    }
}
