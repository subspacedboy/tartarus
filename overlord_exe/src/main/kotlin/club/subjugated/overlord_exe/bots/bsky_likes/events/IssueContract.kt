package club.subjugated.overlord_exe.bots.bsky_likes.events

import org.springframework.context.ApplicationEvent

@Deprecated("Going away")
class IssueContract(
    source: Any,
    val shareableToken: String,
    val goal: Long,
    val did: String
) : ApplicationEvent(source)