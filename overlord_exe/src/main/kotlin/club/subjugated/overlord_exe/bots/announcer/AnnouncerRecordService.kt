package club.subjugated.overlord_exe.bots.announcer

import club.subjugated.fb.message.Contract
import club.subjugated.fb.message.SignedMessage
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.util.TimeSource
import org.springframework.stereotype.Service
import java.nio.ByteBuffer

@Service
class AnnouncerRecordService(
    private var announcerRecordRepository: AnnouncerRecordRepository,
    private var timeSource: TimeSource,
    private var blueSkyService: BlueSkyService
) {
    fun createRecord(token : String, did : String) : AnnouncerRecord {
        val ar = AnnouncerRecord(
            token = token,
            did = did,
            createdAt = timeSource.nowInUtc()
        )

        announcerRecordRepository.save(ar)

        return ar
    }

    fun generateMessageForAccept(shareableToken: String, authorName : String, signedMessageBytes : ByteArray) : String {
        val terms = parseTerms(signedMessageBytes)
        val subject = announcerRecordRepository.findByToken(shareableToken)?.let {
            blueSkyService.resolveDidToHandle(it.did)
        } ?: "Unknown"

//        val author = announcerRecordRepository.findByToken(authorName)?.let {
//            blueSkyService.resolveDidToHandle(it.did)
//        } ?: "Unknown"

        return "$subject has accepted a contract with terms: $terms".take(300)
    }

    fun generateMessageForRelease(shareableToken: String) : String {
        val subject = announcerRecordRepository.findByToken(shareableToken)?.let {
            blueSkyService.resolveDidToHandle(it.did)
        } ?: "Unknown"

//        val author = announcerRecordRepository.findByToken(authorName)?.let {
//            blueSkyService.resolveDidToHandle(it.did)
//        } ?: "Unknown"

        return "$subject has been released".take(300)
    }

    private fun parseTerms(signedMessage : ByteArray) : String {
        val byteBuffer = ByteBuffer.wrap(signedMessage)

        // Parse the FlatBuffer object
        val sm = SignedMessage.getRootAsSignedMessage(byteBuffer)
        val contract = Contract()
        sm.payload(contract)

        return contract.terms!!
    }
}