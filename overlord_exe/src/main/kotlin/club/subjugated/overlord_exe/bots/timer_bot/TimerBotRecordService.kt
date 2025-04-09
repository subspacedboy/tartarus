package club.subjugated.overlord_exe.bots.timer_bot

import club.subjugated.overlord_exe.util.TimeSource
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class TimerBotRecordService(
    private var timerBotRecordRepository: TimerBotRecordRepository,
    private var timeSource: TimeSource
) {
    fun createTimerBotRecord(contractId : Long, endTime: OffsetDateTime) : TimerBotRecord {
        val record = TimerBotRecord(
            contractId = contractId,
            createdAt = timeSource.nowInUtc(),
            endsAt = endTime,
            completed = false
        )

        timerBotRecordRepository.save(record)
        return record
    }

    fun findByContractIds(contractIds : List<Long>) : List<TimerBotRecord> {
        return timerBotRecordRepository.findByContractIdIn(contractIds)
    }

    fun save(record: TimerBotRecord) : TimerBotRecord {
        return timerBotRecordRepository.save(record)
    }
}