package club.subjugated.overlord_exe

import club.subjugated.overlord_exe.cli.CliRoot
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import picocli.CommandLine
import java.security.Security
import kotlin.system.exitProcess

@SpringBootApplication
class OverlordExeApplication

fun main(args: Array<String>) {

	val ctx = SpringApplicationBuilder(OverlordExeApplication::class.java)
//		.web(WebApplicationType.NONE)
		.profiles("cli")
		.run(*args)

	val factory = ctx.getBean(CommandLine.IFactory::class.java)
	val root = ctx.getBean(CliRoot::class.java)

	val cmd = CommandLine(root, factory)

	val exitCode = cmd.execute(*args)
//	exitProcess(exitCode)
}
