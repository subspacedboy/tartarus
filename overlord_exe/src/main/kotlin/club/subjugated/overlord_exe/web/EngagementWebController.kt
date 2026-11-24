package club.subjugated.overlord_exe.web

import club.subjugated.overlord_exe.bots.overlord.OverlordControlBot
import club.subjugated.overlord_exe.machines.SessionEngine
import club.subjugated.overlord_exe.machines.SessionIssueSpec
import club.subjugated.overlord_exe.machines.TimerConfig
import club.subjugated.overlord_exe.models.BSkyCrowdTimeMachineState
import club.subjugated.overlord_exe.models.ReleasePolicy
import club.subjugated.overlord_exe.models.SessionPhase
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.UserProfileService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import jakarta.ws.rs.core.MediaType
import org.ocpsoft.prettytime.PrettyTime
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping

data class SessionIntakeForm(
    var token: String = "",
    @field:NotBlank(message = "Shareable token is required")
    var shareableToken: String = "",
    var public: Boolean = false,
    @field:Size(min = 1, message = "At least one rule/machine must be selected")
    var machines: List<String> = listOf("timer"),
    var releasePolicy: ReleasePolicy = ReleasePolicy.ALL,
    var random: Boolean = false,
    @field:Positive
    var minDuration: Int = 1,
    var minUnit: String = "HOURS",
    @field:Positive
    var maxDuration: Int = 1,
    var maxUnit: String = "HOURS",
)

/**
 * Unified magic-link web controller for composable session intake, machine info requests, and status pages.
 */
@Controller
class EngagementWebController(
    private val overlordControlBot: OverlordControlBot,
    private val sessionEngine: SessionEngine,
    private val userProfileService: UserProfileService,
    private val blueSkyService: BlueSkyService,
) {
    @GetMapping("/session/{token}", produces = [MediaType.APPLICATION_JSON])
    fun getSessionForm(@PathVariable token: String, model: Model): String {
        val session = sessionEngine.findByToken(token)
        if (session.phase != SessionPhase.CREATED) {
            return "no_more_edits"
        }

        val knownTokens = userProfileService.getTokensForUser(session.subjectDid)
        val prefilledToken = session.shareableToken?.takeIf { it.isNotBlank() }
            ?: knownTokens.firstOrNull { it.isDefault }?.token
            ?: knownTokens.firstOrNull()?.token
            ?: ""
        val existingMachines = sessionEngine.findMachines(session.id).map { it.machineType }
            .ifEmpty { listOf("timer") }

        model.addAttribute("hasKeyholder", !session.keyholderDid.isNullOrBlank())
        model.addAttribute("knownTokens", knownTokens)
        model.addAttribute(
            "sessionForm",
            SessionIntakeForm(
                token = session.token,
                shareableToken = prefilledToken,
                public = session.isPublic,
                machines = existingMachines,
                releasePolicy = session.releasePolicy,
            ),
        )
        return "session/form"
    }

    @PostMapping("/session/submit")
    fun submitSessionForm(
        @Valid @ModelAttribute("sessionForm") form: SessionIntakeForm,
        bindingResult: BindingResult,
        model: Model,
    ): String {
        val session = sessionEngine.findByToken(form.token)
        if (bindingResult.hasErrors()) {
            model.addAttribute("hasKeyholder", !session.keyholderDid.isNullOrBlank())
            model.addAttribute("knownTokens", userProfileService.getTokensForUser(session.subjectDid))
            return "session/form"
        }

        sessionEngine.issueSession(
            ctx = overlordControlBot.botContext,
            session = session,
            spec = SessionIssueSpec(
                shareableToken = form.shareableToken,
                isPublic = form.public,
                releasePolicy = form.releasePolicy,
                machineTypes = form.machines,
                timerConfig = TimerConfig(
                    isRandom = form.random,
                    minDuration = form.minDuration,
                    minUnit = form.minUnit,
                    maxDuration = form.maxDuration,
                    maxUnit = form.maxUnit,
                ),
            ),
        )
        return "all_done"
    }

    // --- Machine Info Request Magic Links ---

    @GetMapping("/info/{token}", produces = [MediaType.APPLICATION_JSON])
    fun getInfoForm(@PathVariable token: String, model: Model): String {
        val (templateName, form) = sessionEngine.getInfoForm(token)
            ?: return "no_more_edits"

        model.addAttribute("name", token)
        model.addAttribute("form", form)
        return templateName
    }

    @PostMapping("/info/submit")
    fun submitInfoForm(request: HttpServletRequest): String {
        val params = request.parameterMap.mapValues { (_, values) -> values.firstOrNull() ?: "" }
        sessionEngine.submitInfoForm(params)
        return "all_done"
    }

    // --- Crowd Time Status Page ---

    @GetMapping("/bsky_crowd_time/status/{token}", produces = [MediaType.APPLICATION_JSON])
    fun getStatus(@PathVariable token: String, model: Model): String {
        val machine = sessionEngine.findByStatusToken(token)
            ?: return "no_more_edits"
        val state = machine.state as? BSkyCrowdTimeMachineState
            ?: return "no_more_edits"

        val handle = blueSkyService.resolveDidToHandle(state.subjectDid)
        model.addAttribute("handle", handle)
        model.addAttribute("bSkyCrowdTime", state)
        model.addAttribute("openEndsAt", PrettyTime().format(state.openEndsAt))
        model.addAttribute("endsAt", PrettyTime().format(state.endsAt))
        return "bsky_crowd_time/status"
    }
}
