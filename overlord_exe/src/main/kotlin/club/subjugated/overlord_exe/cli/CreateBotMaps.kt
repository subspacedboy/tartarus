package club.subjugated.overlord_exe.cli

import club.subjugated.overlord_exe.services.BotMapService
import org.springframework.stereotype.Component
import picocli.CommandLine

@Component
@CommandLine.Command(name = "create-bot-maps", description = ["Create mappings for all the bots"])
class CreateBotMaps(
    var botMapService: BotMapService
) : Runnable {

    override fun run() {
        val botMap = botMapService.getOrCreateBotMap("timer", "http://localhost:5002")
        println("Bot Map: ${botMap}")
    }

    //getOrCreateBotMap
}