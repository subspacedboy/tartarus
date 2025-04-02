package club.subjugated.tartarus_coordinator.api.bots

import club.subjugated.fb.bots.BotApiMessage
import club.subjugated.fb.bots.CreateContractRequest
import club.subjugated.fb.bots.CreateContractResponse
import club.subjugated.fb.bots.GetContractRequest
import club.subjugated.fb.bots.GetContractResponse
//import club.subjugated.fb.bots.GetContractRequest
//import club.subjugated.fb.bots.GetContractResponse
import club.subjugated.fb.bots.MessagePayload
import club.subjugated.fb.message.Contract
import club.subjugated.tartarus_coordinator.api.ContractController
import club.subjugated.tartarus_coordinator.api.messages.NewContractMessage
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.ContractService
import club.subjugated.tartarus_coordinator.services.LockSessionService
import club.subjugated.tartarus_coordinator.util.ValidatedPayload
import club.subjugated.tartarus_coordinator.util.signedMessageBytesValidator
import com.google.flatbuffers.FlatBufferBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import java.nio.ByteBuffer
import java.util.*

@Controller
class BotApiController {
//    @Autowired
//    lateinit var contractController: ContractController
    @Autowired
    lateinit var contractService: ContractService
    @Autowired
    lateinit var lockSessionService: LockSessionService
    @Autowired
    lateinit var authorSessionService: AuthorSessionService

    fun routeRequest(message: BotApiMessage) : BotApiMessage {
        when(message.payloadType) {
            MessagePayload.GetContractRequest -> {
                val contractRequest = GetContractRequest()
                message.payload(contractRequest)
                val response = handleGetContractRequest(message.name!!, contractRequest)

                val builder = FlatBufferBuilder(1024)

                val nameOffset = builder.createString("coordinator")
                val responseOffset = builder.createByteVector(response.byteBuffer)

                BotApiMessage.startBotApiMessage(builder)
                BotApiMessage.addName(builder, nameOffset)
                BotApiMessage.addPayload(builder, responseOffset)
                BotApiMessage.addPayloadType(builder, MessagePayload.GetContractResponse)
                BotApiMessage.addRequestId(builder, message.requestId)
                val botApiOffset = BotApiMessage.endBotApiMessage(builder)

                builder.finish(botApiOffset)

                return BotApiMessage.getRootAsBotApiMessage(ByteBuffer.wrap(builder.sizedByteArray()))
            }
            MessagePayload.CreateContractRequest -> {
                val createRequest = CreateContractRequest()
                message.payload(createRequest)
                val response = handleCreateContractRequest(createRequest)

                val builder = FlatBufferBuilder(1024)

                val nameOffset = builder.createString("coordinator")
                val responseOffset = builder.createByteVector(response.byteBuffer)

                BotApiMessage.startBotApiMessage(builder)
                BotApiMessage.addName(builder, nameOffset)
                BotApiMessage.addPayload(builder, responseOffset)
                BotApiMessage.addPayloadType(builder, MessagePayload.CreateContractResponse)
                BotApiMessage.addRequestId(builder, message.requestId)
                val botApiOffset = BotApiMessage.endBotApiMessage(builder)

                builder.finish(botApiOffset)

                return BotApiMessage.getRootAsBotApiMessage(ByteBuffer.wrap(builder.sizedByteArray()))
            }
            else -> {
                TODO()
            }
        }
    }

    fun handleGetContractRequest(botName: String, getContractRequest: GetContractRequest) : GetContractResponse {
        val lockSession = lockSessionService.findBySessionToken(getContractRequest.lockSession!!)
        val contract = contractService.getBySerialAndLockSessionForBot(lockSession, getContractRequest.contractSerialNumber.toInt(), botName)

        val builder = FlatBufferBuilder(1024)
        GetContractResponse.startGetContractResponse(builder)
        GetContractResponse.addNextCounter(builder, contract.nextCounter.toUShort())
        val response = GetContractResponse.endGetContractResponse(builder)
        builder.finish(response)
        return GetContractResponse.getRootAsGetContractResponse(ByteBuffer.wrap(builder.sizedByteArray()))
    }

    fun handleCreateContractRequest(createContractRequest: CreateContractRequest) : CreateContractResponse {
        val signedMessageBytes =
            ByteArray(createContractRequest.contractLength) { createContractRequest.contract(it).toByte() }

        val buf = ByteBuffer.wrap(signedMessageBytes)
        val validated : ValidatedPayload.ContractPayload = signedMessageBytesValidator(buf) as ValidatedPayload.ContractPayload
        val key = ByteArray(validated.contract.publicKeyLength) { validated.contract.publicKey(it).toByte() }

        val authorSession = authorSessionService.saveAuthorSessionForBot(Base64.getEncoder().encodeToString(key))

        val newContractMessage = NewContractMessage(
            shareableToken = createContractRequest.shareableToken!!,
            authorName = authorSession.name, // cheat for now
            signedMessage = Base64.getEncoder().encodeToString(signedMessageBytes),
            notes = ""
        )
        val lockSession = lockSessionService.findByShareableToken(createContractRequest.shareableToken!!)
        val contract = contractService.saveContract(newContractMessage, lockSession!!, authorSession)

        val builder = FlatBufferBuilder(1024)
        CreateContractResponse.startCreateContractResponse(builder)
        val response = CreateContractResponse.endCreateContractResponse(builder)
        builder.finish(response)
        return CreateContractResponse.getRootAsCreateContractResponse(ByteBuffer.wrap(builder.sizedByteArray()))
    }
}