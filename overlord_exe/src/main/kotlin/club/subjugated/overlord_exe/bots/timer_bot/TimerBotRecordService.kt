package club.subjugated.overlord_exe.bots.timer_bot

import club.subjugated.overlord_exe.bots.bsky_likes.BSkyLikeBotRecord
import club.subjugated.overlord_exe.util.TimeSource
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

@Service
class TimerBotRecordService(
    private var timerBotRecordRepository: TimerBotRecordRepository,
    private var timeSource: TimeSource
) {

    fun createInitialPlaceholderRecord(contractSerialNumber: Int, isPublic: Boolean, did: String, amount: Long, unit : String) : TimerBotRecord {
        val record = TimerBotRecord(
            contractSerialNumber = contractSerialNumber,
            createdAt = timeSource.nowInUtc(),
            isPublic = isPublic,
            did = did,
            timeAmount = amount,
            timeUnit = unit
        )

        timerBotRecordRepository.save(record)
        return record
    }

    fun updatePlaceHolderWithContractIdAndCalcEnd(serialNumber: Int, contractId : Long) : TimerBotRecord {
        val record = timerBotRecordRepository.findByContractSerialNumber(serialNumber.toLong())

        val amount = record.timeAmount
        val unit = ChronoUnit.valueOf(record.timeUnit.uppercase())
        val endsAt = timeSource.nowInUtc().plus(amount, unit)

        record.acceptedAt = timeSource.nowInUtc()
        record.endsAt = endsAt
        record.contractId = contractId

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