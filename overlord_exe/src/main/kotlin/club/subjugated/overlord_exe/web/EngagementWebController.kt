package club.subjugated.overlord_exe.web

import club.subjugated.overlord_exe.bots.simple_proxy.SimpleProxyBot
import club.subjugated.overlord_exe.bots.simple_proxy.SimpleProxyIntakeForm
import club.subjugated.overlord_exe.bots.superbot.IntakeForm
import club.subjugated.overlord_exe.bots.superbot.SuperBot
import club.subjugated.overlord_exe.bots.timer_bot.TimeForm
import club.subjugated.overlord_exe.bots.timer_bot.TimerBot
import club.subjugated.overlord_exe.models.BSkyCrowdTimeObjectiveState
import club.subjugated.overlord_exe.models.EngagementPhase
import club.subjugated.overlord_exe.models.SimpleProxyEngagementData
import club.subjugated.overlord_exe.models.SuperBotEngagementData
import club.subjugated.overlord_exe.objectives.ObjectiveService
import club.subjugated.overlord_exe.services.BlueSkyService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.ws.rs.core.MediaType
import org.ocpsoft.prettytime.PrettyTime
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping

/**
 * Unified magic-link web controller for all bot intake forms, objective info requests, and status pages.
 */
@Controller
class EngagementWebController(
    private val timerBot: TimerBot,
    private val superBot: SuperBot,
    private val simpleProxyBot: SimpleProxyBot,
    private val objectiveService: ObjectiveService,
    private val blueSkyService: BlueSkyService,
) {
    // --- TimerBot Magic Link Intake ---

    @GetMapping("/timer/{token}", produces = [MediaType.APPLICATION_JSON])
    fun getTimerForm(@PathVariable token: String, model: Model): String {
        val engagement = timerBot.getSessionByName(token)
        if (engagement.phase != EngagementPhase.CREATED) {
            return "no_more_edits"
        }

        model.addAttribute("name", engagement.name)
        model.addAttribute("timeForm", TimeForm(name = engagement.name, shareableToken = ""))
        return "timer/form"
    }

    @PostMapping("/timer/submit")
    fun submitTimerForm(
        @Valid @ModelAttribute("timeForm") form: TimeForm,
        bindingResult: BindingResult,
        model: Model,
    ): String {
        if (bindingResult.hasErrors()) {
            model.addAttribute("name", form.name)
            return "timer/form"
        }
        timerBot.processContractForm(form)
        return "all_done"
    }

    // --- SuperBot Magic Link Intake ---

    @GetMapping("/superbot/{token}", produces = [MediaType.APPLICATION_JSON])
    fun getSuperBotForm(@PathVariable token: String, model: Model): String {
        val engagement = superBot.findByName(token)
        if (engagement.phase != EngagementPhase.CREATED) {
            return "no_more_edits"
        }

        model.addAttribute("name", engagement.name)
        model.addAttribute(
            "intakeForm",
            IntakeForm(
                name = engagement.name,
                shareableToken = engagement.shareableToken ?: "",
            )
        )
        return "superbot/form"
    }

    @PostMapping("/superbot/submit")
    fun submitSuperBotForm(
        @Valid @ModelAttribute("intakeForm") form: IntakeForm,
        model: Model,
        bindingResult: BindingResult,
    ): String {
        if (bindingResult.hasErrors()) {
            model.addAttribute("name", form.name)
            return "superbot/form"
        }
        superBot.processContractForm(form)
        return "all_done"
    }

    // --- SimpleProxy Magic Link Intake ---

    @GetMapping("/simpleproxy/{token}", produces = [MediaType.APPLICATION_JSON])
    fun getSimpleProxyForm(@PathVariable token: String, model: Model): String {
        val engagement = simpleProxyBot.findByName(token)
        if (engagement.phase != EngagementPhase.CREATED) {
            return "no_more_edits"
        }
        val data = engagement.data as SimpleProxyEngagementData

        model.addAttribute("name", engagement.name)
        model.addAttribute("keyholder", data.keyholderHandle)
        model.addAttribute("sub", data.subHandle)
        model.addAttribute("intakeForm", SimpleProxyIntakeForm(name = engagement.name))
        return "simpleproxy/form"
    }

    @PostMapping("/simpleproxy/submit")
    fun submitSimpleProxyForm(
        @Valid @ModelAttribute("intakeForm") form: SimpleProxyIntakeForm,
        model: Model,
        bindingResult: BindingResult,
    ): String {
        if (bindingResult.hasErrors()) {
            model.addAttribute("name", form.name)
            return "simpleproxy/form"
        }
        simpleProxyBot.processIntakeForm(form)
        return "all_done"
    }

    // --- Objective Info Request Magic Links ---

    @GetMapping("/info/{token}", produces = [MediaType.APPLICATION_JSON])
    fun getInfoForm(@PathVariable token: String, model: Model): String {
        val (templateName, form) = objectiveService.getFormForInfoToken(token)
            ?: return "no_more_edits"

        model.addAttribute("name", token)
        model.addAttribute("form", form)
        return templateName
    }

    @PostMapping("/info/submit")
    fun submitInfoForm(request: HttpServletRequest): String {
        val params = request.parameterMap.mapValues { (_, values) -> values.firstOrNull() ?: "" }
        objectiveService.submitInfoForm(params)
        return "all_done"
    }

    // --- Crowd Time Status Page ---

    @GetMapping("/bsky_crowd_time/status/{token}", produces = [MediaType.APPLICATION_JSON])
    fun getCrowdTimeStatus(@PathVariable token: String, model: Model): String {
        val engagement = objectiveService.findByStatusToken(token)
            ?: throw IllegalStateException("Unknown crowd time status token: $token")
        val data = engagement.data as SuperBotEngagementData
        val state = data.activeObjective as BSkyCrowdTimeObjectiveState

        val handle = blueSkyService.resolveDidToHandle(state.subjectDid)
        model.addAttribute("record", state)
        model.addAttribute("handle", handle)
        model.addAttribute("state", state.phase)

        if (state.endsAt != null) {
            model.addAttribute("endsAtRelative", PrettyTime().format(state.endsAt))
        }
        return "bsky_crowd_time/status"
    }
}
