package club.subjugated.tartarus_coordinator.api.admin

import club.subjugated.tartarus_coordinator.api.messages.NewFirmwareMessage
import club.subjugated.tartarus_coordinator.services.FirmwareService
import jakarta.ws.rs.core.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/firmware")
@Controller
class AdminFirmwareController(
    var firmwareService: FirmwareService
) {
    @PostMapping("/", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    fun addFirmware(
        @AuthenticationPrincipal admin: UserDetails,
        @RequestBody newFirmwareMessage: NewFirmwareMessage
    ) : ResponseEntity<Void> {
        val firmware = firmwareService.createNewFirmware(newFirmwareMessage.firmware)
        println(firmware)
        return ResponseEntity.ok(null)
    }
}