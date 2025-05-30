package club.subjugated.overlord_exe.bots.timer_bot.web

import club.subjugated.overlord_exe.bots.bsky_selflock.BSkySelfLockRecordState
import club.subjugated.overlord_exe.bots.timer_bot.TimerBotRecordService
import club.subjugated.overlord_exe.bots.timer_bot.TimerBotRecordState
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.ws.rs.core.MediaType
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping

data class TimeForm(
    var name: String,
    var shareableToken: String,

    val random: Boolean = false,
    val public: Boolean = false,

    @field:Min(1)
    val minDuration: Int? = 1,
    val minUnit: String = "HOURS",

    @field:Min(1)
    val maxDuration: Int? = 1,
    val maxUnit: String = "HOURS",
)

@RequestMapping("/timer")
@Controller
class TimerBotWebController(
    val timerBotRecordService: TimerBotRecordService
) {
    @GetMapping("/{token}", produces = [MediaType.APPLICATION_JSON])
    fun getPage(@PathVariable token: String, model: Model): String {
        val record = timerBotRecordService.getRecordByName(token)

        if(record.state != TimerBotRecordState.CREATED) {
            return "no_more_edits"
        }

        model.addAttribute("name", record.name)
        model.addAttribute("timeForm", TimeForm(
            name = record.name,
            shareableToken = ""
        )
        )
        return "timer/form"
    }

    @PostMapping("/submit")
    fun handleSubmit(@Valid @ModelAttribute("timeForm") form: TimeForm,
                     model: Model,
                     bindingResult: BindingResult
    ): String {
        if (bindingResult.hasErrors()) {
            model.addAttribute("name", form.name)
            "timer/form" // return to the form page
        }

        timerBotRecordService.processContractForm(form)
        return "all_done"
    }
}