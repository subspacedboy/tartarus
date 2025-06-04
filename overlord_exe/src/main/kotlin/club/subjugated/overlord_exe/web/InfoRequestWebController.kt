package club.subjugated.overlord_exe.web

import club.subjugated.overlord_exe.bots.superbot.SuperBotRecordState
import club.subjugated.overlord_exe.bots.superbot.web.IntakeForm
import club.subjugated.overlord_exe.services.InfoRequestService
import club.subjugated.overlord_exe.statemachines.ContextForm
import jakarta.validation.Valid
import jakarta.ws.rs.core.MediaType
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.primaryConstructor

@Controller
@RequestMapping("/info")
class InfoRequestWebController(
    private val infoRequestService: InfoRequestService
) {
    @GetMapping("/{token}", produces = [MediaType.APPLICATION_JSON])
    fun getPage(@PathVariable token: String, model: Model): String {
        val record = infoRequestService.getRequestByName(token)

        //TODO Add state.
//        if(record.state != SuperBotRecordState.CREATED) {
//            return "no_more_edits"
//        }


        val kClass = Class.forName(record.formType).kotlin
        val instance = kClass.createInstance()

        val name = kClass.qualifiedName!!.lowercase().split(".").last()

        model.addAttribute("name", record.name)
        model.addAttribute("form", instance)
        return "info/$name"
    }

    @PostMapping("/submit")
    fun handleSubmit(@RequestParam params: Map<String, String>,
                     model: Model
    ): String {
        val recordName = params["name"]

        val record = infoRequestService.getRequestByName(recordName!!)

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

        val form = ctor.callBy(args) as ContextForm

        // Optionally validate here, or call your service
        model.addAttribute("form", form)

        infoRequestService.handleResponse(record, form)

        return "all_done"
    }
}