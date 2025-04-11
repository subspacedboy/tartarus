package club.subjugated.overlord_exe.bots.bsky_likes.events

import org.springframework.context.ApplicationEvent

class IssueContract(
    source: Any,
    val shareableToken: String,
    val goal: Long,
    val did: String
) : ApplicationEvent(source)