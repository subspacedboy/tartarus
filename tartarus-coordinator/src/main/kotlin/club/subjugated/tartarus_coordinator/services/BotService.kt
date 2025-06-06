package club.subjugated.tartarus_coordinator.services

import club.subjugated.tartarus_coordinator.api.messages.NewBotMessage
import club.subjugated.tartarus_coordinator.models.Bot
import club.subjugated.tartarus_coordinator.models.BotState
import club.subjugated.tartarus_coordinator.storage.BotRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import club.subjugated.tartarus_coordinator.util.generateSalt
import club.subjugated.tartarus_coordinator.util.getECPublicKeyFromCompressedKeyByteArray
import org.bouncycastle.crypto.CryptoException
import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.util.encoders.Base32
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.*

@Service
class BotService {
    @Autowired lateinit var botRepository: BotRepository

    @Autowired lateinit var timeSource: TimeSource

    fun createNewBot(newBotMessage: NewBotMessage) : Bot {
        try {
            getECPublicKeyFromCompressedKeyByteArray(Base64.getDecoder().decode(newBotMessage.publicKey))
        } catch (cryptoException: CryptoException) {
            throw IllegalArgumentException("Public key couldn't be parsed")
        }

        val bot = Bot(
            state = BotState.ACTIVE,
            publicKey = newBotMessage.publicKey,
            description = newBotMessage.description,
            createdAt = timeSource.nowInUtc(),
            updatedAt = timeSource.nowInUtc()
        )
        bot.generatePassword()
        botRepository.save(bot)

        return bot
    }

    fun getAll() : List<Bot> {
        return botRepository.findAll()
    }

    fun getByName(name : String) : Bot {
        return botRepository.findByName(name)
    }
}