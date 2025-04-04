package club.subjugated.tartarus_coordinator.cli

import club.subjugated.tartarus_coordinator.services.FirmwareService
import org.springframework.stereotype.Component
import picocli.CommandLine
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Component
@CommandLine.Command(name = "add-firmware-image", description = ["Add a new image to the firmware list"])
class AddFirmwareImage(
    var firmwareService: FirmwareService
) : Runnable {
    @CommandLine.Option(names = ["--file"], required = true)
    lateinit var file: String

    override fun run() {
        val byteArray: ByteArray = File(file).readBytes()

        println("Read ${byteArray.size} bytes from $file")

        val buffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)

        if(buffer[0] == 0xE9.toByte()) {
            println("Magic byte matches")
        }

        buffer.position(48)
        val versionBytes = ByteArray(32).also { buffer.get(it) }
        val version = versionBytes.takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8)

//        val projectNameBytes = ByteArray(32).also { buffer.get(it) }
//        val projectName = projectNameBytes.takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8)

        firmwareService.createNewFirmware(byteArray)
    }
}