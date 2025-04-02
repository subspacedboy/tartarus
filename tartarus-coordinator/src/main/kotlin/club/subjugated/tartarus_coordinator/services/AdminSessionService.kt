package club.subjugated.tartarus_coordinator.services

import club.subjugated.tartarus_coordinator.models.AdminSession
import club.subjugated.tartarus_coordinator.storage.AdminSessionRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import club.subjugated.tartarus_coordinator.util.encodePublicKeySecp1
import club.subjugated.tartarus_coordinator.util.getECPublicKeyFromCompressedKeyByteArray
import club.subjugated.tartarus_coordinator.util.loadECPublicKeyFromPem
import club.subjugated.tartarus_coordinator.util.loadECPublicKeyFromPkcs8
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.*

@Service
class AdminSessionService {
    @Autowired
    lateinit var adminSessionRepository: AdminSessionRepository
    @Autowired
    lateinit var timeSource: TimeSource

    fun addAdminSessionKey(keyInBase64 : String) : AdminSession {
        val publicKey = getECPublicKeyFromCompressedKeyByteArray(Base64.getDecoder().decode(keyInBase64))
        val encoded = Base64.getEncoder().encodeToString(encodePublicKeySecp1(publicKey))

        assert(keyInBase64 == encoded)

        val adminSession = findByPublicKey(keyInBase64) ?: run {
            val adminSession = AdminSession(
                publicKey = encoded,
                createdAt = timeSource.nowInUtc(),
                updatedAt = timeSource.nowInUtc()
            )

            adminSessionRepository.save(adminSession)
            adminSession
        }

        return adminSession
    }

    fun findByName(name : String) : AdminSession {
        return adminSessionRepository.findByName(name)
    }

    fun findByPublicKey(publicKey : String) : AdminSession? {
        return adminSessionRepository.findByPublicKey(publicKey)
    }
}