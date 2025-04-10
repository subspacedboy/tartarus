package club.subjugated.overlord_exe.bots.timer_bot.events

import org.springframework.context.ApplicationEvent

class IssueContract(
    source: Any,
    val shareableToken: String
) : ApplicationEvent(source)