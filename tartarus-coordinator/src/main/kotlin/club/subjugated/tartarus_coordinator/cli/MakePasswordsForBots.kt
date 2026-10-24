package club.subjugated.tartarus_coordinator.cli

import org.springframework.stereotype.Component
import picocli.CommandLine

@Component
@CommandLine.Command(
    name = "make-passwords-for-bots",
    description = ["Deprecated - bots now authenticate via ECDSA JWTs and AuthorSession ownership"],
)
class MakePasswordsForBots : Runnable {
    override fun run() {
        println("Deprecated: bots no longer use MQTT passwords.")
    }
}
