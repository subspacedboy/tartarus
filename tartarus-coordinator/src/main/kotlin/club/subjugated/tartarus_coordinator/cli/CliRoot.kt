package club.subjugated.tartarus_coordinator.cli

import org.springframework.stereotype.Component
import picocli.CommandLine

@CommandLine.Command(
    name = "tartarus",
    subcommands = [
        AddAdminKeyCommand::class,
//        ListUsersCli::class
    ],
    description = ["Tartarus CLI entry point"]
)
@Component
class CliRoot : Runnable{
    override fun run() {
        println("Specify a subcommand. Use --help for options.")
    }
}