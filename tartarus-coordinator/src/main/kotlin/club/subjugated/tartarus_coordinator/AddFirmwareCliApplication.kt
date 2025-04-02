package club.subjugated.tartarus_coordinator

import club.subjugated.tartarus_coordinator.services.FirmwareService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

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

//        val descOffset = 0x100
//        val desc = byteArray.copyOfRange(descOffset, descOffset + 256)
        val buffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)

        if(buffer[0] == 0xE9.toByte()) {
            println("Magic byte matches")
        }

        buffer.position(48)
        val versionBytes = ByteArray(32).also { buffer.get(it) }
        val version = versionBytes.takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8)

        val projectNameBytes = ByteArray(32).also { buffer.get(it) }
        val projectName = projectNameBytes.takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8)


        firmwareService.createNewFirmware(byteArray, version)

        // Your command line logic here
    }
}

fun main(args: Array<String>) {
//    runApplication<TartarusCoordinatorApplication>(*args)
    SpringApplicationBuilder(AddFirmwareCliApplication::class.java)
        .web(org.springframework.boot.WebApplicationType.NONE)
        .run(*args)
}