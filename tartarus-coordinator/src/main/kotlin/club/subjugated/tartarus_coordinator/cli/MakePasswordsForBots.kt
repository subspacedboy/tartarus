package club.subjugated.tartarus_coordinator.cli

import club.subjugated.tartarus_coordinator.services.BotService
import org.springframework.stereotype.Component
import picocli.CommandLine

@Component
@CommandLine.Command(name = "make-passwords-for-bots", description = ["Migration - make sure bots have passwords"])
class MakePasswordsForBots(
    private var botService: BotService
) : Runnable {
    override fun run() {
        val bots = botService.getAll()

        for(b in bots) {
            if(b.password.isNullOrEmpty()) {
                b.generatePassword()
                botService.botRepository.save(b)
                println("${b.name} -> ${b.clearTextPassword}")
            }
        }
    }
}