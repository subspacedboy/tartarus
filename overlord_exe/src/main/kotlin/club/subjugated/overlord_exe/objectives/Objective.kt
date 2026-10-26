package club.subjugated.overlord_exe.objectives

import club.subjugated.overlord_exe.models.BSkyCrowdTimeObjectiveState
import club.subjugated.overlord_exe.models.BotEngagement
import club.subjugated.overlord_exe.models.ObjectiveState
import club.subjugated.overlord_exe.models.ObjectiveStatus
import club.subjugated.overlord_exe.models.SuperBotEngagementData
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.UrlService
import club.subjugated.overlord_exe.storage.BotEngagementRepository
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.generateId
import org.springframework.stereotype.Service

interface Objective<S : ObjectiveState> {
    val objectiveId: String
    val templateName: String

    fun createInitialState(subjectDid: String): S
    fun createEmptyForm(token: String): Any
    fun applyFormParams(state: S, params: Map<String, String>)
    fun evaluate(state: S, engagement: BotEngagement)
}

@Service
class ObjectiveService(
    private val objectives: List<Objective<*>>,
    private val engagementRepository: BotEngagementRepository,
    private val blueSkyService: BlueSkyService,
    private val urlService: UrlService,
    private val timeSource: TimeSource,
) {
    private val objectivesById: Map<String, Objective<ObjectiveState>> by lazy {
        @Suppress("UNCHECKED_CAST")
        objectives.associate { (it as Objective<ObjectiveState>).objectiveId to it }
    }

    fun getObjective(objectiveId: String): Objective<ObjectiveState> {
        return objectivesById[objectiveId]
            ?: throw IllegalArgumentException("Unknown objective: $objectiveId")
    }

    fun startObjective(engagement: BotEngagement, objectiveId: String): ObjectiveState {
        val objective = getObjective(objectiveId)
        val state = objective.createInitialState(engagement.subjectDid)
        val data = engagement.data as? SuperBotEngagementData
            ?: SuperBotEngagementData(objectives = listOf(objectiveId)).also { engagement.data = it }
        data.activeObjective = state
        if (state is BSkyCrowdTimeObjectiveState) {
            engagement.statusToken = state.name
        }
        process(engagement)
        return state
    }

    fun process(engagement: BotEngagement): ObjectiveState? {
        val data = engagement.data as? SuperBotEngagementData ?: return null
        val state = data.activeObjective ?: return null
        val objective = getObjective(state.objectiveId)

        when (state.status) {
            ObjectiveStatus.NEEDS_INFO -> {
                val token = generateId("ir-")
                engagement.infoToken = token
                state.status = ObjectiveStatus.WAITING
                saveEngagement(engagement)
                if (engagement.convoId.isNotBlank()) {
                    blueSkyService.sendDm(engagement.convoId, urlService.generateUrl("info/$token"))
                }
            }
            ObjectiveStatus.WAITING -> {
                // Waiting for user web form submission
            }
            ObjectiveStatus.IN_PROGRESS -> {
                objective.evaluate(state, engagement)
                saveEngagement(engagement)
            }
            ObjectiveStatus.COMPLETE -> {}
        }
        return state
    }

    fun findByInfoToken(token: String): BotEngagement? {
        return engagementRepository.findByInfoToken(token)
    }

    fun findByStatusToken(token: String): BotEngagement? {
        return engagementRepository.findByStatusToken(token)
    }

    fun getFormForInfoToken(token: String): Pair<String, Any>? {
        val engagement = engagementRepository.findByInfoToken(token) ?: return null
        val data = engagement.data as? SuperBotEngagementData ?: return null
        val state = data.activeObjective ?: return null
        if (state.status != ObjectiveStatus.WAITING) {
            return null
        }
        val objective = getObjective(state.objectiveId)
        return objective.templateName to objective.createEmptyForm(token)
    }

    fun submitInfoForm(params: Map<String, String>): BotEngagement {
        val token = params["name"] ?: throw IllegalArgumentException("Missing info token 'name'")
        val engagement = engagementRepository.findByInfoToken(token)
            ?: throw IllegalStateException("Unknown or already submitted info token: $token")
        val data = engagement.data as SuperBotEngagementData
        val state = data.activeObjective
            ?: throw IllegalStateException("Engagement ${engagement.name} has no active objective")
        val objective = getObjective(state.objectiveId)

        objective.applyFormParams(state, params)
        state.status = ObjectiveStatus.IN_PROGRESS
        engagement.infoToken = null
        objective.evaluate(state, engagement)
        return saveEngagement(engagement)
    }

    fun saveEngagement(engagement: BotEngagement): BotEngagement {
        engagement.syncJsonBeforeSave()
        engagement.updatedAt = timeSource.nowInUtc()
        return engagementRepository.save(engagement)
    }
}
