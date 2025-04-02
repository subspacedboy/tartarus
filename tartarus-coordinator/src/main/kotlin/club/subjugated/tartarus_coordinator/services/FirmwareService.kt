package club.subjugated.tartarus_coordinator.services

import club.subjugated.fb.message.firmware.FirmwareChallengeRequest
import club.subjugated.fb.message.firmware.FirmwareMessage
import club.subjugated.fb.message.firmware.MessagePayload
import club.subjugated.tartarus_coordinator.events.FirmwareValidationEvent
import club.subjugated.tartarus_coordinator.models.Firmware
import club.subjugated.tartarus_coordinator.models.FirmwareState
import club.subjugated.tartarus_coordinator.storage.FirmwareRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import club.subjugated.tartarus_coordinator.util.getECPublicKeyFromCompressedKeyByteArray
import club.subjugated.tartarus_coordinator.util.rawToDerSignature
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.flatbuffers.FlatBufferBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.min

@Service
class FirmwareService {
    @Autowired
    lateinit var firmwareRepository: FirmwareRepository
    @Autowired
    lateinit var timeSource: TimeSource

    @Autowired lateinit var publisher: ApplicationEventPublisher

    @Value("\${tartarus.firmware.challenge_key}")
    var challengeKey : String = ""

    private val nonceChallenges: Cache<Long, ByteArray> =
        Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(10).build()

    private val firmwareImages: Cache<String, ByteArray> =
        Caffeine.newBuilder().maximumSize(2).build()

    fun createNewFirmware(image : ByteArray, version : String) : Firmware {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(image)
        val hash = Base64.getEncoder().encodeToString(digest.digest())

        val firmware : Firmware = firmwareRepository.findFirstByDigest(hash) ?: run {
            val f = Firmware(
                state = FirmwareState.ACTIVE,
                image = image,
                signature = "",
                digest = hash,
                version = version,
                createdAt = timeSource.nowInUtc()
            )
            firmwareRepository.save(f)
        }
        return firmware
    }

    fun getLatest() : Firmware {
        return firmwareRepository.findFirstByOrderByCreatedAtDesc()
    }

    fun getFirmwareBytes(name : String, size: Int, offset : Int) : ByteArray {
        val image = firmwareImages.get(name) {
            val firmware = firmwareRepository.findByName(name)
            firmware.image!!
        }

        if(offset > image.size) {
            throw IllegalArgumentException("Requested beyond the bounds of the image")
        }
        var end = offset+size
        if(end > image.lastIndex){
            end = image.lastIndex
        }

        // +1 because copyOfRange is end-exclusive
        return image.copyOfRange(offset, end+1)
    }

    fun generateFirmwareChallenge(sessionToken: String) : ByteArray {
        val builder = FlatBufferBuilder(1024)

        val nonce = ByteArray(32) // P-256 (secp256r1) requires 32 bytes (256 bits)
        SecureRandom().nextBytes(nonce)

        val nonceOffset = builder.createByteVector(nonce)
        FirmwareChallengeRequest.startFirmwareChallengeRequest(builder)
        FirmwareChallengeRequest.addNonce(builder, nonceOffset)
        val challengeOffset = FirmwareChallengeRequest.endFirmwareChallengeRequest(builder)

        val sessionTokenOffset = builder.createString(sessionToken)

        FirmwareMessage.startFirmwareMessage(builder)
        FirmwareMessage.addPayload(builder, challengeOffset)
        FirmwareMessage.addPayloadType(builder, MessagePayload.FirmwareChallengeRequest)
        FirmwareMessage.addSessionToken(builder, sessionTokenOffset)
        val requestId = SecureRandom().nextLong()
        nonceChallenges.put(requestId, nonce)
        FirmwareMessage.addRequestId(builder, requestId)
        val firmwareOffset = FirmwareMessage.endFirmwareMessage(builder)

        builder.finish(firmwareOffset)
        return builder.sizedByteArray()
    }

    fun validateChallengeSignature(requestId: Long, sessionToken: String, data : ByteArray) : Boolean {
        val challengeKey = getECPublicKeyFromCompressedKeyByteArray(Base64.getDecoder().decode(challengeKey))

        val nonce = nonceChallenges.getIfPresent(requestId)!!

        val verifier = Signature.getInstance("NonewithECDSA", "BC").apply {
            initVerify(challengeKey)
            update(nonce)
        }
        val signature = rawToDerSignature(data)

        val validated = verifier.verify(signature)

        val event = FirmwareValidationEvent(
            this,
            sessionToken = sessionToken,
            validated = validated
        )
        publisher.publishEvent(event)

        return validated
    }
}