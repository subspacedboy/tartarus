package club.subjugated.tartarus_coordinator.api

import club.subjugated.tartarus_coordinator.api.messages.ConfigurationMessage
import club.subjugated.tartarus_coordinator.services.ConfigurationService
import jakarta.ws.rs.core.MediaType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/configuration")
@Controller
class ConfigurationController {
    @Autowired
    lateinit var configurationService: ConfigurationService

    @GetMapping("/", produces = [MediaType.APPLICATION_JSON])
    @ResponseBody
    fun getConfigurationBlob() : ResponseEntity<ConfigurationMessage> {
        val configuration = this.configurationService.getConfigurationAsMessage()
        return ResponseEntity.ok(configuration)
    }
}