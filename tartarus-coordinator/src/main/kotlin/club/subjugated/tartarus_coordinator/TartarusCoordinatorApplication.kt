package club.subjugated.tartarus_coordinator

import club.subjugated.tartarus_coordinator.cli.AddAdminKeyCommand
import club.subjugated.tartarus_coordinator.cli.CliRoot
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import picocli.CommandLine
import kotlin.system.exitProcess

@SpringBootApplication
@ComponentScan(basePackages = ["club.subjugated"])
class TartarusCoordinatorApplication

fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        val ctx = SpringApplicationBuilder(TartarusCoordinatorApplication::class.java)
            .web(WebApplicationType.NONE)
            .profiles("cli")
            .run(*args)

        val factory = ctx.getBean(CommandLine.IFactory::class.java)
        val root = ctx.getBean(CliRoot::class.java)

        val cmd = CommandLine(root, factory)

        val exitCode = cmd.execute(*args)
        exitProcess(exitCode)
    } else {
        runApplication<TartarusCoordinatorApplication>(*args)
    }
}
