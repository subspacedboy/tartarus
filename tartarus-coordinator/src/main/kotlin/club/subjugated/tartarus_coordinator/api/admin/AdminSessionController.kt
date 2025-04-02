package club.subjugated.tartarus_coordinator.api.admin

import club.subjugated.tartarus_coordinator.api.messages.AdminSessionMessage
import club.subjugated.tartarus_coordinator.api.messages.ConfirmAdminSessionMessage
import club.subjugated.tartarus_coordinator.services.AdminSessionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/admin_sessions")
@Controller
class AdminSessionController {
    @Autowired
    lateinit var adminSessionService: AdminSessionService

    @PostMapping("/")
    fun confirmAdminSession(@RequestBody confirmAdminSessionMessage: ConfirmAdminSessionMessage) : ResponseEntity<AdminSessionMessage> {
        confirmAdminSessionMessage.validateOrThrow()

        val adminSession = adminSessionService.findByPublicKey(confirmAdminSessionMessage.publicKey!!)

        return ResponseEntity.ok(AdminSessionMessage(name = adminSession!!.name))
    }
}