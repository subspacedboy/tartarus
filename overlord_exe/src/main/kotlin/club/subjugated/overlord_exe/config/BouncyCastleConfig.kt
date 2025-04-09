package club.subjugated.overlord_exe.config

import jakarta.annotation.PostConstruct
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.springframework.context.annotation.Configuration
import java.security.Security

@Configuration
class BouncyCastleConfig {
    init {
        Security.addProvider(BouncyCastleProvider())
    }
}