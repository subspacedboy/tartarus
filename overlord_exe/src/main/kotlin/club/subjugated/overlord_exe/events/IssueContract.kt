package club.subjugated.overlord_exe.events

import club.subjugated.overlord_exe.models.BotMap
import org.springframework.context.ApplicationEvent

class IssueContract(
    source: Any,
    val botMap: BotMap,
    val serialNumberRecorder : (serialNumber:Int) -> (Unit),
    val recordName: String,
    val shareableToken: String,
    val public: Boolean,
    val terms: String
) : ApplicationEvent(source)