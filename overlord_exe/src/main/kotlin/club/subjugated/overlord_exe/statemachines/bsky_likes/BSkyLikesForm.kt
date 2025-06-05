package club.subjugated.overlord_exe.statemachines.bsky_likes
import club.subjugated.overlord_exe.statemachines.ContextForm

data class BSkyLikesForm (
    override var name: String = "",
    var did: String? = "",
    var goal: Long = 10
) : ContextForm {
    override fun validate(): List<String> {
        val result = mutableListOf<String>()
        if(goal < 0) {
            result.add("Goal has to be greater than zero.")
        }

        return result
    }
}