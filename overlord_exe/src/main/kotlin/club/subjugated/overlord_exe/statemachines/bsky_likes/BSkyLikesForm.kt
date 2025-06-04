package club.subjugated.overlord_exe.statemachines.bsky_likes
import club.subjugated.overlord_exe.statemachines.ContextForm

data class BSkyLikesForm (
    var name: String = "",
    var did: String = "",
    var goal: Long = 10
) : ContextForm