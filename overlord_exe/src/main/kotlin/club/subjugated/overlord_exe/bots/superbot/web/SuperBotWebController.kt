package club.subjugated.overlord_exe.bots.superbot.web

import club.subjugated.overlord_exe.bots.superbot.SuperBot
import club.subjugated.overlord_exe.bots.superbot.SuperBotSessionState
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import jakarta.ws.rs.core.MediaType
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping

data class IntakeForm(
    var name: String,
    var shareableToken: String,
    var public: Boolean = false,

    @field:Size(min = 1, message = "At least one objective must be selected")
    var objectives: List<String> = listOf("bsky_likes", "bsky_crowd_time")
)

@RequestMapping("/superbot")
@Controller
class SuperBotWebController(
    private val superBot: SuperBot
) {
    @GetMapping("/{token}", produces = [MediaType.APPLICATION_JSON])
    fun getPage(@PathVariable token: String, model: Model): String {
        val session = superBot.findByName(token)

        if(session.state != SuperBotSessionState.CREATED) {
            return "no_more_edits"
        }

        model.addAttribute("name", session.name)
        model.addAttribute("intakeForm", IntakeForm(
            name = session.name,
            shareableToken = session.shareableToken ?: ""
        )
        )
        return "superbot/form"
    }

    @PostMapping("/submit")
    fun handleSubmit(@Valid @ModelAttribute("intakeForm") form: IntakeForm,
                     model: Model,
                     bindingResult: BindingResult
    ): String {
        if (bindingResult.hasErrors()) {
            model.addAttribute("name", form.name)
            "superbot/form" // return to the form page
        }

        superBot.processContractForm(form)
        return "all_done"
    }
}