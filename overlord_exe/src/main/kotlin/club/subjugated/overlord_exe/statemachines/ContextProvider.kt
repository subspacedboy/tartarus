package club.subjugated.overlord_exe.statemachines

import club.subjugated.overlord_exe.models.StateMachine

interface ContextForm {
    var name: String
    fun validate(): List<String>
}

interface Context {
    fun receive(form: ContextForm)
}

interface ContextProvider<F : ContextForm, C : Context> {
    /**
     * Logical identifier for this objective/state-machine type (e.g. "bsky_likes", "bsky_crowd_time").
     */
    val objectiveId: String
        get() = this.javaClass.name

    /**
     * Form type identifier stored on [club.subjugated.overlord_exe.models.InfoRequest.formType].
     */
    val formTypeName: String
        get() = ""

    /**
     * Thymeleaf template path used by `/info/{token}` (e.g. "info/bskylikesform").
     */
    val templateName: String
        get() = ""

    fun createEmptyForm(requestName: String): F? = null
    fun bindForm(params: Map<String, String>): F? = null

    fun createContext(stateMachineId: Long, form: F): C
    fun findByStateMachineId(stateMachineId: Long): C
    fun saveContext(ctx: C): C
    fun getInitialState(): String
    fun process(stateMachine: StateMachine, ctx: C)
}
