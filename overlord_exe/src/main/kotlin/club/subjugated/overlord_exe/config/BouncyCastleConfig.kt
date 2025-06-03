package club.subjugated.overlord_exe.config

import jakarta.annotation.PostConstruct
import jakarta.annotation.Priority
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.security.Security

@Component
@Order(value = Ordered.HIGHEST_PRECEDENCE)
@Priority(value = 100)
class BouncyCastleConfig {
    @PostConstruct
    fun registerProvider() {
        println("Registering BouncyCastle provider")
        Security.addProvider(BouncyCastleProvider())
    }
}