package club.subjugated.tartarus_coordinator

import club.subjugated.tartarus_coordinator.services.FirmwareService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import java.io.File

@SpringBootApplication
@ComponentScan(basePackages = ["club.subjugated"])
class AddFirmwareCliApplication : CommandLineRunner {
    @Autowired
    lateinit var firmwareService: FirmwareService

    override fun run(vararg args: String?) {
        println("Running without web server")

        val filePath = "/tmp/image"
        val byteArray: ByteArray = File(filePath).readBytes()

        println("Read ${byteArray.size} bytes from $filePath")

        firmwareService.createNewFirmware(byteArray, 0, 0, 1)

        // Your command line logic here
    }
}

fun main(args: Array<String>) {
//    runApplication<TartarusCoordinatorApplication>(*args)
    SpringApplicationBuilder(AddFirmwareCliApplication::class.java)
        .web(org.springframework.boot.WebApplicationType.NONE)
        .run(*args)
}