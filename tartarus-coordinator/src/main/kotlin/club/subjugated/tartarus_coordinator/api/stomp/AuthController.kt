package club.subjugated.tartarus_coordinator.api.stomp

import com.fasterxml.jackson.annotation.JsonFormat
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.stereotype.Controller
import java.security.SecureRandom

@Controller
class AuthController {
    @MessageMapping("/auth")  // Listens for messages sent to "/app/auth"
    @SendTo("/topic/challenge")  // Sends response to "/topic/challenge"
    fun handleAuthRequest(name: String): String {
        val nonce = ByteArray(32)
        SecureRandom().nextBytes(nonce)

//        val challenge = AuthChallenge.newBuilder()
//            .setName(name)
//            .setNonce(com.google.protobuf.ByteString.copyFrom(nonce))
//            .build()

        return JsonFormat.printer().print(challenge)
    }
}