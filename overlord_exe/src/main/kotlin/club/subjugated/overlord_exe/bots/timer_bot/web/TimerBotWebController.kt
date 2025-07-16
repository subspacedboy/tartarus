package club.subjugated.overlord_exe.bots.timer_bot.web

import club.subjugated.overlord_exe.bots.timer_bot.TimerBotRecordService
import club.subjugated.overlord_exe.bots.timer_bot.TimerBotRecordState
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Pattern
import jakarta.ws.rs.core.MediaType
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindException
import org.springframework.validation.BindingResult
import org.springframework.web.bind.WebDataBinder
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.InitBinder
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import java.time.temporal.ChronoUnit

data class TimeForm(
    var name: String,

    @field:Pattern(
        regexp = "^(?:s|tc)-[0-9a-zA-Z]{7}$",
        message = "Shareable token must start with S or TC, have a hyphen, and have 7 characters"
    )
    var shareableToken: String,

    var random: Boolean = false,
    var public: Boolean = false,

    @field:Positive
    var minDuration: Int? = 1,
    var minUnit: String = "HOURS",

    @field:Positive
    var maxDuration: Int? = 1,
    var maxUnit: String = "HOURS",
) {

    @get:AssertTrue(message = "Minimum duration must be less than maximum duration")
    val validDuration: Boolean
        get() {
            if (minDuration == null || maxDuration == null) return true
            return try {
                val min = ChronoUnit.valueOf(minUnit.uppercase()).duration.seconds * minDuration!!
                val max = ChronoUnit.valueOf(maxUnit.uppercase()).duration.seconds * maxDuration!!
                min <= max
            } catch (e: Exception) {
                true // fail open if units are invalid; field-level validators will catch it
            }
        }
}

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
                     bindingResult: BindingResult,
                     model: Model,
    ): String {
        if (bindingResult.hasErrors()) {
            model.addAttribute("name", form.name)
            return "timer/form" // return to the form page
        }

        println("Processing timer form: $form")
        timerBotRecordService.processContractForm(form)
        return "all_done"
    }
}