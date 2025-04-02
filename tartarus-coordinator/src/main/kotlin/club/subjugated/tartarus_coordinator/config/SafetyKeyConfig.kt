package club.subjugated.tartarus_coordinator.config

import club.subjugated.tartarus_coordinator.services.SafetyKeyService
import jakarta.annotation.PostConstruct
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class SafetyKeyConfig {
    @Autowired lateinit var safetyKeyService: SafetyKeyService

    @PostConstruct
    fun init() {
        Security.addProvider(BouncyCastleProvider())

        val safetyKeys = safetyKeyService.getAllActiveSafetyKeys()
        if (safetyKeys.isEmpty()) {
            println("No safety keys were found. Creating a new one.")
            safetyKeyService.createNewSafetyKey()
        }
    }
}
