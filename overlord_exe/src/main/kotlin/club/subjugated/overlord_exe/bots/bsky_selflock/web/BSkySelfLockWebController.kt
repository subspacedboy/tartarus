package club.subjugated.overlord_exe.bots.bsky_selflock.web

import club.subjugated.overlord_exe.bots.bsky_selflock.BSkySelfLockRecordState
import club.subjugated.overlord_exe.bots.bsky_selflock.BSkySelfLockService
import club.subjugated.overlord_exe.services.BlueSkyService
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.ws.rs.core.MediaType
import org.ocpsoft.prettytime.PrettyTime
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

    @field:Min(1)
    val duration: Int? = 1,
    val unit: String = "HOURS",
    val public: Boolean = false,

    @field:Min(1)
    val perLikeAdd: Int? = 5,
    val perLikeAddUnit: String = "MINUTES",

    @field:Min(1)
    val perRepostAdd: Int? = 1,
    val perRepostAddUnit: String = "HOURS",

    @field:Min(1)
    val openPeriodAmount: Int? = 1,
    val openPeriodUnit: String = "HOURS"
)


@RequestMapping("/bsky_selflock")
@Controller
class BSkySelfLockWebController(
    val bSkySelfLockService: BSkySelfLockService,
    val blueSkyService: BlueSkyService
) {
    @GetMapping("/{token}", produces = [MediaType.APPLICATION_JSON])
    fun getPage(@PathVariable token: String, model: Model): String {
        val record = bSkySelfLockService.getRecord(token)

        if(record.state != BSkySelfLockRecordState.CREATED) {
            return "no_more_edits"
        }

        model.addAttribute("name", record.name)
        model.addAttribute("timeForm", TimeForm(
            name = record.name,
            shareableToken = ""
            )
        )
        return "bsky_selflock/form"
    }

    @PostMapping("/submit")
    fun handleSubmit(@Valid @ModelAttribute("timeForm") form: TimeForm,
                     model: Model,
                     bindingResult: BindingResult
    ): String {
        if (bindingResult.hasErrors()) {
            model.addAttribute("name", form.name)
            "bsky_selflock/form" // return to the form page
        }

        bSkySelfLockService.processContractForm(form)
        return "all_done"
    }

    @GetMapping("/status/{token}", produces = [MediaType.APPLICATION_JSON])
    fun getStatus(@PathVariable token: String, model: Model): String {
        val record = bSkySelfLockService.getRecord(token)

        val handle = blueSkyService.resolveDidToHandle(record.did)
        model.addAttribute("record", record)
        model.addAttribute("handle", handle)
        if(record.endsAt != null) {
            val endsAtRelative = PrettyTime().format(record.endsAt)
            model.addAttribute("endsAtRelative", endsAtRelative)
        }
        return "bsky_selflock/status"
    }
}