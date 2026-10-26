package club.subjugated.overlord_exe.services

import club.subjugated.overlord_exe.events.RequestInfo
import club.subjugated.overlord_exe.models.InfoRequest
import club.subjugated.overlord_exe.models.InfoRequestState
import club.subjugated.overlord_exe.models.StateMachine
import club.subjugated.overlord_exe.statemachines.ContextForm
import club.subjugated.overlord_exe.statemachines.InfoResolveMethod
import club.subjugated.overlord_exe.storage.InfoRequestRepository
import club.subjugated.overlord_exe.util.TimeSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class InfoRequestService @Autowired constructor(
    private val infoRequestRepository: InfoRequestRepository,
    private val stateMachineService: StateMachineService,
    private val timeSource: TimeSource,
    private val urlService: UrlService,
    @Autowired(required = false) private val blueSkyService: BlueSkyService? = null,
) {
    fun createInfoRequest(stateMachine: StateMachine, formType: String): InfoRequest {
        val ir = InfoRequest(
            state = InfoRequestState.CREATED,
            stateMachine = stateMachine,
            formType = formType,
            bskyUser = stateMachine.bskyUser!!,
            updatedAt = timeSource.nowInUtc(),
            createdAt = timeSource.nowInUtc(),
        )
        infoRequestRepository.save(ir)
        return ir
    }

    fun requestInfo(stateMachine: StateMachine, formType: String): InfoRequest {
        val ir = createInfoRequest(stateMachine, formType)

        if (stateMachine.infoResolveMethod == InfoResolveMethod.USER) {
            val url = urlService.generateUrl("info/${ir.name}")
            val user = stateMachine.bskyUser!!
            val convoId = user.convoId
            if (!convoId.isNullOrBlank()) {
                blueSkyService?.sendDm(convoId, "Information needed: $url")
            }
        }
        return ir
    }

    fun getRequestByName(name: String): InfoRequest {
        return infoRequestRepository.findByName(name)
    }

    fun handleResponse(record: InfoRequest, form: ContextForm) {
        val stateMachine = stateMachineService.findByName(record.stateMachine.name)
        stateMachine.context!!.receive(form)
        stateMachineService.process(stateMachine)
        stateMachineService.save(stateMachine)

        record.state = InfoRequestState.COMPLETE
        infoRequestRepository.save(record)
    }

    @EventListener
    fun handleInfoRequest(event: RequestInfo) {
        requestInfo(event.stateMachine, event.formClass)
    }
}
