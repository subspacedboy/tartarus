package club.subjugated.overlord_exe.events

class SendDmEvent (
    val source: Any,
    val message: String,
    val did: String,
    val convoId: String,
    val bskyUserName : String? = null
)