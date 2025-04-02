package club.subjugated.tartarus_coordinator.services

import club.subjugated.tartarus_coordinator.models.SafetyKey
import club.subjugated.tartarus_coordinator.models.SafetyKeyState
import club.subjugated.tartarus_coordinator.storage.SafetyKeyRepository
import club.subjugated.tartarus_coordinator.util.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class SafetyKeyService {
    @Autowired
    lateinit var safetyKeyRepository: SafetyKeyRepository
    @Autowired
    lateinit var timeSource: TimeSource

    fun createNewSafetyKey() : SafetyKey {
        val keyPair = generateECKeyPair()
        val key = SafetyKey(
            state = SafetyKeyState.ACTIVE,
            privateKey = encodePrivateKey(keyPair.private),
            publicKey = encodePublicKey(keyPair.public),
            createdAt = timeSource.nowInUtc()
        )
        this.safetyKeyRepository.save(key)
        return key
    }


    fun getAllActiveSafetyKeys() : List<SafetyKey> {
        return safetyKeyRepository.getAllByState(SafetyKeyState.ACTIVE)
    }
}