package club.subjugated.tartarus_coordinator.api.admin

import club.subjugated.tartarus_coordinator.api.messages.*
import club.subjugated.tartarus_coordinator.services.HardwareVerificationService
import jakarta.ws.rs.core.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/hardware", "/admin/hardware")
class HardwareProvisioningAdminController(
    private val hardwareVerificationService: HardwareVerificationService
) {

    @PostMapping("/provision", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    fun provisionUnit(
        @RequestBody request: ProvisioningIngestRequest
    ): ResponseEntity<ProvisionedHardwareUnitDetailDto> {
        val result = hardwareVerificationService.ingestAndSave(request)
        return ResponseEntity.ok(result)
    }

    @PostMapping("/verify-preview", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    fun verifyPreview(
        @RequestBody request: ProvisioningIngestRequest
    ): ResponseEntity<ProvisioningPreviewResponse> {
        val preview = hardwareVerificationService.verifyPreview(request)
        return ResponseEntity.ok(preview)
    }

    @GetMapping("/units", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    fun listUnits(
        @RequestParam(name = "mode", required = false) mode: String?,
        @RequestParam(name = "revoked", required = false) revoked: Boolean?,
        @RequestParam(name = "search", required = false) search: String?,
    ): ResponseEntity<List<ProvisionedHardwareUnitSummaryDto>> {
        val units = hardwareVerificationService.listUnits(mode, revoked, search)
        return ResponseEntity.ok(units)
    }

    @GetMapping("/units/{hardwareId}", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    fun getUnitDetail(
        @PathVariable("hardwareId") hardwareId: String
    ): ResponseEntity<ProvisionedHardwareUnitDetailDto> {
        val unit = hardwareVerificationService.getUnitDetail(hardwareId)
        return ResponseEntity.ok(unit)
    }

    @PatchMapping("/units/{hardwareId}", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    fun updateUnit(
        @PathVariable("hardwareId") hardwareId: String,
        @RequestBody request: UpdateHardwareUnitRequest,
    ): ResponseEntity<ProvisionedHardwareUnitDetailDto> {
        val updated = hardwareVerificationService.updateUnit(hardwareId, request)
        return ResponseEntity.ok(updated)
    }
}
