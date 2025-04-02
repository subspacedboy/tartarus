package club.subjugated.tartarus_coordinator.services

import club.subjugated.tartarus_coordinator.models.Firmware
import club.subjugated.tartarus_coordinator.models.FirmwareState
import club.subjugated.tartarus_coordinator.storage.FirmwareRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import kotlin.math.min

@Service
class FirmwareService {
    @Autowired
    lateinit var firmwareRepository: FirmwareRepository
    @Autowired
    lateinit var timeSource: TimeSource

    fun createNewFirmware(image : ByteArray, major: Long, minor: Long, build: Long) : Firmware {
        val firmware = Firmware(
            state = FirmwareState.ACTIVE,
            image = image,
            signature = "",
            major = major,
            minor = minor,
            build = build,
            createdAt = timeSource.nowInUtc()
        )

        firmwareRepository.save(firmware)
        return firmware
    }

    fun getLatest() : Firmware {
        return firmwareRepository.findFirstByOrderByCreatedAtDesc()
    }
}