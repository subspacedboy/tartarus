package club.subjugated.tartarus_coordinator.cli

import club.subjugated.tartarus_coordinator.services.AdminSessionService
import org.springframework.stereotype.Component
import picocli.CommandLine

@Component
@CommandLine.Command(name = "add-admin-key", description = ["Add a new admin key"])
class AddAdminKeyCommand(
    var adminSessionService: AdminSessionService
) : Runnable {
    @CommandLine.Option(names = ["--key"], required = true)
    lateinit var key: String

    override fun run() {
        val adminSession = adminSessionService.addAdminSessionKey(keyInBase64 = key)
        println("Admin session name: ${adminSession.name}")
    }
}