package club.subjugated.tartarus_coordinator.api.firmware

import club.subjugated.fb.bots.GetContractRequest
import club.subjugated.fb.message.firmware.FirmwareMessage
import club.subjugated.fb.message.firmware.GetLatestFirmwareRequest
import club.subjugated.fb.message.firmware.GetLatestFirmwareResponse
import club.subjugated.fb.message.firmware.MessagePayload
import club.subjugated.fb.message.firmware.Version
import club.subjugated.tartarus_coordinator.api.bots.ResponseBuilderContext
import club.subjugated.tartarus_coordinator.services.FirmwareService
import com.google.flatbuffers.FlatBufferBuilder
import com.google.flatbuffers.Table
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import java.nio.ByteBuffer
import java.security.MessageDigest

@Controller
class FirmwareApiController {
    @Autowired
    lateinit var firmwareService: FirmwareService

    fun routeRequest(message: FirmwareMessage) : FirmwareMessage {
        when(message.payloadType) {
            MessagePayload.GetLatestFirmwareRequest -> {
                val latestRequest = GetLatestFirmwareRequest()
                message.payload(latestRequest)
                val response = handleGetLatestFirmwareRequest(latestRequest)
                return buildResponse(MessagePayload.GetLatestFirmwareResponse, message.requestId, message.sessionToken!!, response)
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

        Version.startVersion(builder)
        Version.addMajor(builder, 0.toUShort())
        Version.addMinor(builder, 0.toUShort())
        Version.addBuild(builder, 0.toUShort())
        val versionOffset = Version.endVersion(builder)

        val size = firmware.image!!.size
        val digest = MessageDigest.getInstance("SHA-256")
        val digestValue = digest.digest(firmware.image!!)

        val subset = firmware.image!!.take(5_000).toByteArray()
        val imageOffset = builder.createByteVector(ByteBuffer.wrap(subset))
        val nameOffset = builder.createString(firmware.name)
        val hashOffset = builder.createByteVector(digestValue)

        GetLatestFirmwareResponse.startGetLatestFirmwareResponse(builder)
        GetLatestFirmwareResponse.addVersion(builder, versionOffset)
        GetLatestFirmwareResponse.addSize(builder, size.toUShort())
        GetLatestFirmwareResponse.addName(builder, nameOffset)
        GetLatestFirmwareResponse.addSignature(builder, hashOffset)

        GetLatestFirmwareResponse.addFirmware(builder, imageOffset)
        val getLatestFirmwareVersionResponseOffset = GetLatestFirmwareResponse.endGetLatestFirmwareResponse(builder)

        return ResponseBuilderContext(builder, getLatestFirmwareVersionResponseOffset)
    }
}