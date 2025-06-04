package club.subjugated.overlord_exe.services

import club.subjugated.overlord_exe.events.RequestInfo
import club.subjugated.overlord_exe.events.SendDmEvent
import club.subjugated.overlord_exe.models.BSkyUser
import club.subjugated.overlord_exe.models.InfoRequest
import club.subjugated.overlord_exe.models.StateMachine
import club.subjugated.overlord_exe.statemachines.Context
import club.subjugated.overlord_exe.statemachines.ContextForm
import club.subjugated.overlord_exe.statemachines.InfoResolveMethod
import club.subjugated.overlord_exe.storage.InfoRequestRepository
import club.subjugated.overlord_exe.util.TimeSource
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class InfoRequestService(
    private val infoRequestRepository: InfoRequestRepository,
    private val stateMachineService: StateMachineService,
    private val timeSource: TimeSource,
    private val urlService: UrlService,
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    fun createInfoRequest(stateMachine : StateMachine, formType : String) : InfoRequest {
        val ir = InfoRequest(
            stateMachine = stateMachine,
            formType = formType,
            bskyUser = stateMachine.bskyUser!!,
            updatedAt = timeSource.nowInUtc(),
            createdAt = timeSource.nowInUtc()
        )
        infoRequestRepository.save(ir)
        return ir
    }

    fun getRequestByName(name : String) : InfoRequest {
        return infoRequestRepository.findByName(name)
    }

    fun handleResponse(record : InfoRequest, form : ContextForm) {
        // Right now we load it again because the hibenate version isn't
        // going to include the underlying context that actually needs/processes
        // the info
        val stateMachine = stateMachineService.findByName(record.stateMachine.name)
        stateMachine.context!!.receive(form)
        stateMachineService.process(stateMachine)
        stateMachineService.save(stateMachine)
    }

    @EventListener
    fun handleInfoRequest(event : RequestInfo) {
        val stateMachine = event.stateMachine
        val ir = createInfoRequest(event.stateMachine, event.formClass)

        if(stateMachine.infoResolveMethod == InfoResolveMethod.USER) {
            val url = urlService.generateUrl("ir/${ir.name}")
            val user = stateMachine.bskyUser!!

            applicationEventPublisher.publishEvent(SendDmEvent(
                source = this,
                message = "Information needed: $url",
                convoId = user.convoId!!,
                did = user.did
            ))
        }
    }
}