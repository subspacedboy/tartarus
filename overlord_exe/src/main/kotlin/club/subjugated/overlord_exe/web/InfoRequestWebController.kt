package club.subjugated.overlord_exe.web

import club.subjugated.overlord_exe.models.InfoRequestState
import club.subjugated.overlord_exe.services.InfoRequestService
import club.subjugated.overlord_exe.services.StateMachineService
import club.subjugated.overlord_exe.statemachines.ContextForm
import jakarta.ws.rs.core.MediaType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.primaryConstructor

@Controller
@RequestMapping("/info")
class InfoRequestWebController @Autowired constructor(
    private val infoRequestService: InfoRequestService,
    @Autowired(required = false) private val stateMachineService: StateMachineService? = null,
) {
    @GetMapping("/{token}", produces = [MediaType.APPLICATION_JSON])
    fun getPage(@PathVariable token: String, model: Model): String {
        val record = infoRequestService.getRequestByName(token)

        if (record.state != InfoRequestState.CREATED) {
            return "no_more_edits"
        }

        val provider = stateMachineService?.resolveProviderByFormType(record.formType)
        val instance = provider?.createEmptyForm(record.name) ?: run {
            val kClass = Class.forName(record.formType).kotlin
            (kClass.createInstance() as ContextForm).also { it.name = record.name }
        }
        val template = provider?.templateName?.takeIf { it.isNotBlank() } ?: run {
            val name = record.formType.lowercase().split(".").last()
            "info/$name"
        }

        model.addAttribute("form", instance)
        return template
    }

    @PostMapping("/submit")
    fun handleSubmit(
        @RequestParam params: Map<String, String>,
        model: Model,
    ): String {
        val recordName = params["name"]!!
        val record = infoRequestService.getRequestByName(recordName)

        val provider = stateMachineService?.resolveProviderByFormType(record.formType)
        val form: ContextForm = provider?.bindForm(params) ?: run {
            val kClass = Class.forName(record.formType).kotlin
            val ctor = kClass.primaryConstructor ?: error("No primary constructor found")
            val args = ctor.parameters.associateWith { param ->
                val rawValue = params[param.name]
                when (param.type.classifier) {
                    Long::class -> rawValue?.toLongOrNull()
                    Int::class -> rawValue?.toIntOrNull()
                    Boolean::class -> rawValue?.toBoolean()
                    else -> rawValue
                }
            }
            ctor.callBy(args) as ContextForm
        }

        val validationResult = form.validate()
        if (validationResult.isNotEmpty()) {
            model.addAttribute("name", record.name)
            model.addAttribute("form", form)

            val template = provider?.templateName?.takeIf { it.isNotBlank() } ?: run {
                val name = record.formType.lowercase().split(".").last()
                "info/$name"
            }
            return template
        }

        model.addAttribute("form", form)
        infoRequestService.handleResponse(record, form)

        return "all_done"
    }
}
