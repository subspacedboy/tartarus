package club.subjugated.overlord_exe.web

import jakarta.ws.rs.core.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/")
class RootController {
    @GetMapping("/", produces = [MediaType.APPLICATION_JSON])
    fun getRoot() : String {
        return "welcome"
    }
}