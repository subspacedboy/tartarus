package club.subjugated.overlord_exe.cli

import org.springframework.stereotype.Component
import picocli.CommandLine

@CommandLine.Command(
    name = "overlord_exe",
    subcommands = [
        GetBskyJwt::class,
    ],
    description = ["Overlord.exe CLI entry point"]
)
@Component
class CliRoot : Runnable {
    override fun run() {
        println("Specify a subcommand. Use --help for options.")
    }
}