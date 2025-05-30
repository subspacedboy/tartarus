package club.subjugated.overlord_exe.bots.timer_bot.events

import org.springframework.context.ApplicationEvent

class IssueContract(
    source: Any,
    val recordName: String,
    val shareableToken: String,
//    val amount: Long,
//    val unit: String,
    val public: Boolean,
    val terms: String
) : ApplicationEvent(source)