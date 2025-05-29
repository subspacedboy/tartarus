package club.subjugated.overlord_exe.bots.bsky_selflock

import club.subjugated.overlord_exe.bots.bsky_selflock.events.IssueContract
import club.subjugated.overlord_exe.bots.bsky_selflock.web.TimeForm
import club.subjugated.overlord_exe.cli.LikeRecord
import club.subjugated.overlord_exe.cli.RepostRecord
import club.subjugated.overlord_exe.cli.parseBlueskyUri
import club.subjugated.overlord_exe.components.SendDmEvent
import club.subjugated.overlord_exe.models.Contract
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.services.UrlService
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.extractShareableToken
import club.subjugated.overlord_exe.util.formatDuration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import work.socialhub.kbsky.util.ATUri
import java.time.Duration
import java.time.temporal.ChronoUnit

@Service
class BSkySelfLockService(
    val bSkySelfLockRepository: BSkySelfLockRepository,
    val applicationEventPublisher : ApplicationEventPublisher,
    val blueSkyService: BlueSkyService,
    val urlService: UrlService,
    val timeSource: TimeSource
) {
    fun createPlaceholder(did : String, convoId : String) : String {
        val record = BSkySelfLockBotRecord(
            contractSerialNumber = 0,
            state = BSkySelfLockRecordState.CREATED,
            did = did,
            convoId = convoId,
            createdAt = timeSource.nowInUtc()
        )
        bSkySelfLockRepository.save(record)
        return record.name
    }

    fun processAccept(contractSerialNumber : Int, contract: Contract) {
        val record = bSkySelfLockRepository.findByContractSerialNumber(contractSerialNumber)
        record.contractId = contract.id
        record.acceptedAt = timeSource.nowInUtc()

        val subjectHandle = blueSkyService.resolveDidToHandle(record.did)

        val openPeriod = formatDuration(record.openPeriodAmount, record.openPeriodUnit)
        val likePeriod = formatDuration(record.perLikeAdd, record.perLikeAddUnit)
        val repostPeriod = formatDuration(record.perRepostAdd, record.perRepostAddUnit)

        val infoUrl = urlService.generateUrl("bsky_selflock/status/${record.name}")

        val announcement = """
            $subjectHandle has been locked. For the next $openPeriod after $subjectHandle reposts this, you may like and repost this notice.
            1 like = $likePeriod
            1 repost = $repostPeriod
            
            More Information: $infoUrl
        """.trimIndent()

        val uri = blueSkyService.post(announcement)
        record.noticeUri = uri
        record.state = BSkySelfLockRecordState.OPEN_POSTED
        saveRecord(record)
    }

    fun checkIfNoticeReposted(record : BSkySelfLockBotRecord) {
        val reposts = blueSkyService.getReposts(record.noticeUri)
        if(reposts.any {it.did == record.did}) {
            record.state = BSkySelfLockRecordState.IN_OPEN
            record.repostedNoticeAt = timeSource.nowInUtc()

            val openPeriodDuration = Duration.of(record.openPeriodAmount.toLong(), ChronoUnit.valueOf(record.openPeriodUnit))
            record.openEndsAt = record.repostedNoticeAt!!.plus(openPeriodDuration)
            saveRecord(record)
        }
    }

    fun checkIfOpenEndedAndCalculate(record: BSkySelfLockBotRecord) {
        val openDuration = Duration.of(record.openPeriodAmount.toLong(), ChronoUnit.valueOf(record.openPeriodUnit))

        val totalLikes = HashSet<LikeRecord>()
        val totalReposts = HashSet<RepostRecord>()

        blueSkyService.traceThread(record.noticeUri) {
                post ->
            val likes = blueSkyService.getLikes(post.uri!!)
            likes.forEach {
                totalLikes.add(LikeRecord(it.actor.did))
            }

            val reposts = blueSkyService.getReposts(post.uri!!)
            reposts.forEach {
                // Exclude the author's mandatory repost.
                if(it.did != record.did) {
                    totalReposts.add(RepostRecord(it.did))
                }
            }
        }

        val likeTime = Duration.of(totalLikes.size.toLong() * record.perLikeAdd, ChronoUnit.valueOf(record.perLikeAddUnit))
        val repostTime = Duration.of(totalReposts.size.toLong() * record.perRepostAdd, ChronoUnit.valueOf(record.perRepostAddUnit))

        val minPeriod = Duration.of(record.durationAmount.toLong(), ChronoUnit.valueOf(record.durationUnit))

        val newTime = record.repostedNoticeAt!!.plus(likeTime).plus(repostTime).plus(minPeriod)
        record.endsAt = newTime

        if(record.repostedNoticeAt!!.plus(openDuration) < timeSource.nowInUtc()) {
            record.state = BSkySelfLockRecordState.CLOSED
        }
        saveRecord(record)
    }

    fun findByContractIds(contractIds : List<Long>) : List<BSkySelfLockBotRecord> {
        return bSkySelfLockRepository.findByContractIdIn(contractIds)
    }

    fun getRecord(name: String) : BSkySelfLockBotRecord {
        return bSkySelfLockRepository.findByName(name)
    }

    fun recordSerialNumberForName(name: String, serialNumber : Int) {
        val record = getRecord(name)
        record.contractSerialNumber = serialNumber
        saveRecord(record)
    }

    fun processContractForm(timeForm : TimeForm) {
        val record = bSkySelfLockRepository.findByName(timeForm.name)

        val token = extractShareableToken(timeForm.shareableToken)
        record.shareableToken = token

        val durationAmount = timeForm.duration
        val durationUnit = ChronoUnit.valueOf(timeForm.unit)
        val minimumDuration = Duration.of(durationAmount!!.toLong(), durationUnit)

        record.durationAmount = durationAmount
        record.durationUnit = timeForm.unit

        val amountPerLike = timeForm.perLikeAdd!!
        val perLikeUnit = ChronoUnit.valueOf(timeForm.perLikeAddUnit)
        val perLikeDuration = Duration.of(amountPerLike.toLong(), perLikeUnit)

        record.perLikeAdd = amountPerLike
        record.perLikeAddUnit = timeForm.perLikeAddUnit

        val amountPerRepost = timeForm.perRepostAdd!!
        val perRepostUnit = ChronoUnit.valueOf(timeForm.perRepostAddUnit)
        val perRepostDuration = Duration.of(amountPerRepost.toLong(), perRepostUnit)

        record.perRepostAdd = amountPerRepost
        record.perRepostAddUnit = timeForm.perRepostAddUnit

        val openAmount = timeForm.openPeriodAmount!!
        val openAmountUnit = ChronoUnit.valueOf(timeForm.openPeriodUnit)
        val openPeriodDuration = Duration.of(openAmount.toLong(), openAmountUnit)

        record.openPeriodAmount = openAmount
        record.openPeriodUnit = timeForm.openPeriodUnit

        record.state = BSkySelfLockRecordState.ISSUED
        bSkySelfLockRepository.save(record)

        // Issue contract.
        applicationEventPublisher.publishEvent(IssueContract(
            source = this,
            name = record.name,
            shareableToken = token,
            did = record.did,
            convoId = record.convoId
        ))

        applicationEventPublisher.publishEvent(SendDmEvent(
            source = this,
            message = "Contract issued",
            convoId = record.convoId,
            did = record.did
        ))
    }

    fun saveRecord(record : BSkySelfLockBotRecord) {
        bSkySelfLockRepository.save(record)
    }
}