package club.subjugated.tartarus_coordinator.api.firmware

import club.subjugated.fb.message.firmware.FirmwareChallengeResponse
import club.subjugated.fb.message.firmware.FirmwareMessage
import club.subjugated.fb.message.firmware.GetFirmwareChunkRequest
import club.subjugated.fb.message.firmware.GetFirmwareChunkResponse
import club.subjugated.fb.message.firmware.GetLatestFirmwareRequest
import club.subjugated.fb.message.firmware.GetLatestFirmwareResponse
import club.subjugated.fb.message.firmware.MessagePayload
import club.subjugated.tartarus_coordinator.api.bots.ResponseBuilderContext
import club.subjugated.tartarus_coordinator.services.FirmwareService
import club.subjugated.tartarus_coordinator.services.LockSessionService
import com.google.flatbuffers.FlatBufferBuilder
import com.google.flatbuffers.Table
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import java.nio.ByteBuffer
import java.util.*

@Controller
class FirmwareApiController {
    @Autowired
    lateinit var firmwareService: FirmwareService
    @Autowired
    lateinit var lockSessionService: LockSessionService

    fun routeRequest(message: FirmwareMessage) : FirmwareMessage? {
        when(message.payloadType) {
            MessagePayload.GetLatestFirmwareRequest -> {
                val latestRequest = GetLatestFirmwareRequest()
                message.payload(latestRequest)
                val response = handleGetLatestFirmwareRequest(latestRequest)
                return buildResponse(MessagePayload.GetLatestFirmwareResponse, message.requestId, message.sessionToken!!, response)
            }
            MessagePayload.GetFirmwareChunkRequest -> {
                val getFirmwareChunk = GetFirmwareChunkRequest()
                message.payload(getFirmwareChunk)
                val response = handleGetFirmwareChunkRequest(message.requestId, getFirmwareChunk)
                return buildResponse(MessagePayload.GetFirmwareChunkResponse, message.requestId, message.sessionToken!!, response)
            }
            MessagePayload.FirmwareChallengeResponse -> {
                val challengeResponse = FirmwareChallengeResponse()
                message.payload(challengeResponse)
                handleChallengeResponse(message.requestId, message.sessionToken!!, challengeResponse)
                return null
            }
            else -> {
                TODO()
            }
        }
    }

    private fun <T : Table> buildResponse(payloadType: UByte, requestId: Long, sessionToken: String, builderContext: ResponseBuilderContext<T>) : FirmwareMessage {
        val builder = builderContext.builder

        val sessionTokenOffset = builder.createString(sessionToken)

        FirmwareMessage.startFirmwareMessage(builder)
        FirmwareMessage.addPayload(builder, builderContext.messageOffset)
        FirmwareMessage.addPayloadType(builder, payloadType)
        FirmwareMessage.addSessionToken(builder, sessionTokenOffset)
        FirmwareMessage.addRequestId(builder, requestId)

        val firmwareMessageOffset = FirmwareMessage.endFirmwareMessage(builder)

        builder.finish(firmwareMessageOffset)
        return FirmwareMessage.getRootAsFirmwareMessage(ByteBuffer.wrap(builder.sizedByteArray()))

    }

    fun handleGetLatestFirmwareRequest(message : GetLatestFirmwareRequest) : ResponseBuilderContext<GetLatestFirmwareResponse> {
        val firmware = firmwareService.getLatest()

        val builder = FlatBufferBuilder(1024)

        val size = firmware.image!!.size

        val firmware_name_offset = builder.createString(firmware.name)
        val versionNameOffset = builder.createString(firmware.version)
        val hashOffset = builder.createByteVector(Base64.getDecoder().decode(firmware.digest))

        GetLatestFirmwareResponse.startGetLatestFirmwareResponse(builder)
        GetLatestFirmwareResponse.addSize(builder, size)
        GetLatestFirmwareResponse.addFirmwareName(builder, firmware_name_offset)
        GetLatestFirmwareResponse.addVersionName(builder, versionNameOffset)
        GetLatestFirmwareResponse.addDigest(builder, hashOffset)

        val getLatestFirmwareVersionResponseOffset = GetLatestFirmwareResponse.endGetLatestFirmwareResponse(builder)

        return ResponseBuilderContext(builder, getLatestFirmwareVersionResponseOffset)
    }

    fun handleGetFirmwareChunkRequest(requestId: Long, message: GetFirmwareChunkRequest) : ResponseBuilderContext<GetFirmwareChunkResponse> {
        val builder = FlatBufferBuilder(1024)

        val firmwareName = message.firmwareName!!
        val bytesToGet = message.size
        val fromOffset = message.offset

        val bytes = firmwareService.getFirmwareBytes(firmwareName, bytesToGet, fromOffset)

        val bytesOffset = builder.createByteVector(bytes)
        GetFirmwareChunkResponse.startGetFirmwareChunkResponse(builder)
        GetFirmwareChunkResponse.addSize(builder, bytes.size)
        GetFirmwareChunkResponse.addOffset(builder, message.offset)
        GetFirmwareChunkResponse.addChunk(builder, bytesOffset)
        val getFirmwareChunkOffset = GetFirmwareChunkResponse.endGetFirmwareChunkResponse(builder)

        return ResponseBuilderContext(builder, getFirmwareChunkOffset)
    }

    fun handleChallengeResponse(requestId: Long, sessionToken: String, message : FirmwareChallengeResponse) {
        val signatureBytes = ByteArray(message.signatureLength) { message.signature(it).toByte() }

        val validated = firmwareService.validateChallengeSignature(requestId, sessionToken, signatureBytes)
        println("🧑‍🔬 Firmware validated as official? -> $validated")
    }
}