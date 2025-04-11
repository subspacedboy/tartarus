package club.subjugated.overlord_exe.bots.bsky_likes

import club.subjugated.overlord_exe.bots.timer_bot.TimerBotRecord
import club.subjugated.overlord_exe.util.TimeSource
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class BSkyLikesBotService(
    private var bSkyLikesBotRecordRepository: BSkyLikesBotRecordRepository,
    private var timeSource: TimeSource
) {
    fun createInitialPlaceholderRecord(contractSerialNumber: Int, authorDid: String, goal: Long) : BSkyLikeBotRecord {
        val record = BSkyLikeBotRecord(
            contractSerialNumber = contractSerialNumber,
            createdAt = timeSource.nowInUtc(),
            goal = goal,
            likesSoFar = 0,
            did = authorDid,
            completed = false,
        )

        bSkyLikesBotRecordRepository.save(record)
        return record
    }

    fun updatePlaceHolderWithContractId(serialNumber: Int, contractId : Long) : BSkyLikeBotRecord {
        val record = bSkyLikesBotRecordRepository.findByContractSerialNumber(serialNumber)
        record.acceptedAt = timeSource.nowInUtc()
        record.contractId = contractId

        bSkyLikesBotRecordRepository.save(record)
        return record
    }

    fun findByContractIds(contractIds : List<Long>) : List<BSkyLikeBotRecord> {
        return bSkyLikesBotRecordRepository.findByContractIdIn(contractIds)
    }

    fun save(record: BSkyLikeBotRecord) : BSkyLikeBotRecord {
        return bSkyLikesBotRecordRepository.save(record)
    }
}