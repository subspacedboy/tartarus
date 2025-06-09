package club.subjugated.overlord_exe.bots.simple_proxy.web

import club.subjugated.overlord_exe.bots.simple_proxy.SimpleProxyService
import club.subjugated.overlord_exe.bots.simple_proxy.SimpleProxyState
import club.subjugated.overlord_exe.bots.superbot.SuperBotRecordState
import club.subjugated.overlord_exe.bots.superbot.web.IntakeForm
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

data class SimpleProxyIntakeForm(
    var name: String,
    var public: Boolean = false,
)

@RequestMapping("/simpleproxy")
@Controller
class SimpleProxyWebController(
    private val simpleProxyService: SimpleProxyService
) {
    @GetMapping("/{token}", produces = [MediaType.APPLICATION_JSON])
    fun getPage(@PathVariable token: String, model: Model): String {
        val record = simpleProxyService.findByName(token)

        if(record.state != SimpleProxyState.CREATED) {
            return "no_more_edits"
        }

        model.addAttribute("name", record.name)
        model.addAttribute("keyholder", record.keyHolderBskyUser.handle)
        model.addAttribute("sub", record.bskyUser.handle)
        model.addAttribute("intakeForm", SimpleProxyIntakeForm(
            name = record.name,
        )
        )
        return "simpleproxy/form"
    }

    @PostMapping("/submit")
    fun handleSubmit(@Valid @ModelAttribute("intakeForm") form: SimpleProxyIntakeForm,
                     model: Model,
                     bindingResult: BindingResult
    ): String {
        if (bindingResult.hasErrors()) {
            model.addAttribute("name", form.name)
            "simpleproxy/form" // return to the form page
        }

        simpleProxyService.processIntakeForm(form)
        return "all_done"
    }
}