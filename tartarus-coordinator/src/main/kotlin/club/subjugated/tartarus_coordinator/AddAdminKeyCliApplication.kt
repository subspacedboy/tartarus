package club.subjugated.tartarus_coordinator

import club.subjugated.tartarus_coordinator.components.MqttBroker
import club.subjugated.tartarus_coordinator.models.AdminSession
import club.subjugated.tartarus_coordinator.services.AdminSessionService
import club.subjugated.tartarus_coordinator.services.MqttListenerService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import picocli.CommandLine

@SpringBootApplication
@ComponentScan(basePackages = ["club.subjugated"])
class AddAdminKeyCliApplication: CommandLineRunner {
    @Autowired
    lateinit var adminSessionService: AdminSessionService

    override fun run(vararg args: String?) {
        val mainClass = System.getProperty("sun.java.command") ?: return
        if (!mainClass.contains("AddAdminKeyCliApplication")) return

        val spec = CommandLine.Model.CommandSpec.create()

        val nameOpt = CommandLine.Model.OptionSpec.builder("-key", "--key")
            .paramLabel("key")
            .description("Key in secp1 compressed form in base64")
            .type(String::class.java)
            .build()
        spec.addOption(nameOpt)

        val parseResult: CommandLine.ParseResult = CommandLine(spec).parseArgs(*args)

        val key = parseResult.matchedOptionValue("key", null as String?)
        val adminSession = adminSessionService.addAdminSessionKey(keyInBase64 = key!!)

        println("Admin session name: ${adminSession.name}")
    }
}

fun main(args: Array<String>) {
    SpringApplicationBuilder(AddAdminKeyCliApplication::class.java)
        .web(org.springframework.boot.WebApplicationType.NONE)
        .profiles("cli")
        .run(*args)
}