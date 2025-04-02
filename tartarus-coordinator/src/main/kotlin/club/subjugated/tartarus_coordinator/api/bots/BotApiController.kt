package club.subjugated.tartarus_coordinator.api.bots

import club.subjugated.fb.bots.BotApiMessage
import club.subjugated.fb.bots.CreateCommandRequest
import club.subjugated.fb.bots.CreateCommandResponse
import club.subjugated.fb.bots.CreateContractRequest
import club.subjugated.fb.bots.CreateContractResponse
import club.subjugated.fb.bots.CreateMessageRequest
import club.subjugated.fb.bots.CreateMessageResponse
import club.subjugated.fb.bots.Error
import club.subjugated.fb.bots.GetContractRequest
import club.subjugated.fb.bots.GetContractResponse
import club.subjugated.fb.bots.GetLockSessionRequest
import club.subjugated.fb.bots.GetLockSessionResponse
//import club.subjugated.fb.bots.GetContractRequest
//import club.subjugated.fb.bots.GetContractResponse
import club.subjugated.fb.bots.MessagePayload
import club.subjugated.fb.message.Contract
import club.subjugated.tartarus_coordinator.api.ContractController
import club.subjugated.tartarus_coordinator.api.messages.NewContractMessage
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.BotService
import club.subjugated.tartarus_coordinator.services.ContractService
import club.subjugated.tartarus_coordinator.services.LockSessionService
import club.subjugated.tartarus_coordinator.util.ValidatedPayload
import club.subjugated.tartarus_coordinator.util.encodePublicKeySecp1
import club.subjugated.tartarus_coordinator.util.getECPublicKeyFromCompressedKeyByteArray
import club.subjugated.tartarus_coordinator.util.loadECPublicKeyFromPkcs8
import club.subjugated.tartarus_coordinator.util.signedMessageBytesValidator
import com.google.flatbuffers.FlatBufferBuilder
import com.google.flatbuffers.Table
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import java.nio.ByteBuffer
import java.util.*

class ResponseBuilderContext<T>(
    var builder: FlatBufferBuilder,
    var messageOffset: Int,
)

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
    @Autowired
    lateinit var botService: BotService

    fun routeRequest(message: BotApiMessage) : BotApiMessage {
        when(message.payloadType) {
            MessagePayload.GetContractRequest -> {
                val contractRequest = GetContractRequest()
                message.payload(contractRequest)
                val response = handleGetContractRequest(message.name!!, contractRequest)
                return buildResponse(MessagePayload.GetContractResponse, message.requestId, response)
            }
            MessagePayload.CreateContractRequest -> {
                val createRequest = CreateContractRequest()
                message.payload(createRequest)
                val response = handleCreateContractRequest(createRequest)
                return buildResponse(MessagePayload.CreateContractResponse, message.requestId, response)
            }
            MessagePayload.GetLockSessionRequest -> {
                val getLockSessionRequest = GetLockSessionRequest()
                message.payload(getLockSessionRequest)
                val builderContext = handleGetLockSessionRequest(getLockSessionRequest)
                return buildResponse(MessagePayload.GetLockSessionResponse, message.requestId, builderContext)
            }
            MessagePayload.CreateCommandRequest -> {
                val createCommandRequest = CreateCommandRequest()
                message.payload(createCommandRequest)
                val builderContext = handleCreateCommandRequest(message.name!!, createCommandRequest)
                return buildResponse(MessagePayload.CreateCommandResponse, message.requestId, builderContext)
            }
            MessagePayload.CreateMessageRequest -> {
                val createMessageRequest = CreateMessageRequest()
                message.payload(createMessageRequest)
                val builderContext = handleCreateMessageRequest(message.name!!, createMessageRequest)
                return buildResponse(MessagePayload.CreateMessageResponse, message.requestId, builderContext)
            }
            else -> {
                TODO()
            }
        }
    }

    private fun <T : Table> buildResponse(payloadType: UByte, requestId: Long, builderContext: ResponseBuilderContext<T>?) : BotApiMessage {
        if(builderContext != null) {
            val builder = builderContext.builder

            val nameOffset = builder.createString("coordinator")

            BotApiMessage.startBotApiMessage(builder)
            BotApiMessage.addName(builder, nameOffset)
            BotApiMessage.addPayload(builder, builderContext.messageOffset)
            BotApiMessage.addPayloadType(builder, payloadType)
            BotApiMessage.addRequestId(builder, requestId)
            val botApiOffset = BotApiMessage.endBotApiMessage(builder)

            builder.finish(botApiOffset)
            return BotApiMessage.getRootAsBotApiMessage(ByteBuffer.wrap(builder.sizedByteArray()))
        }

        val builder = FlatBufferBuilder(1024)

        val nameOffset = builder.createString("coordinator")
//        val responseOffset = builder.createByteVector(response.byteBuffer)

        BotApiMessage.startBotApiMessage(builder)
        BotApiMessage.addName(builder, nameOffset)

//        BotApiMessage.addPayload(builder, response.byteBuffer)
        BotApiMessage.addPayloadType(builder, payloadType)
        BotApiMessage.addRequestId(builder, requestId)
        val botApiOffset = BotApiMessage.endBotApiMessage(builder)

        builder.finish(botApiOffset)
        return BotApiMessage.getRootAsBotApiMessage(ByteBuffer.wrap(builder.sizedByteArray()))
    }

    fun handleGetContractRequest(botName: String, getContractRequest: GetContractRequest) : ResponseBuilderContext<GetContractResponse> {
        val lockSession = lockSessionService.findBySessionToken(getContractRequest.lockSession!!)
        val contract = contractService.getBySerialAndLockSessionForBot(lockSession!!, getContractRequest.contractSerialNumber.toInt(), botName)

        val builder = FlatBufferBuilder(1024)

        val stateOffset = builder.createString(contract.state.toString())
        val nameOffset = builder.createString(contract.name)

        GetContractResponse.startGetContractResponse(builder)
        GetContractResponse.addNextCounter(builder, contract.nextCounter.toUShort())
        GetContractResponse.addName(builder, nameOffset)
        GetContractResponse.addState(builder, stateOffset)
        val response = GetContractResponse.endGetContractResponse(builder)
        return ResponseBuilderContext(builder, response)
    }

    fun handleCreateContractRequest(createContractRequest: CreateContractRequest) : ResponseBuilderContext<CreateContractResponse> {
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
        val nameOffset = builder.createString(contract.name)
        CreateContractResponse.startCreateContractResponse(builder)
        CreateContractResponse.addContractName(builder, nameOffset)
        val response = CreateContractResponse.endCreateContractResponse(builder)
        return ResponseBuilderContext(builder, response)
    }

    fun handleGetLockSessionRequest(request : GetLockSessionRequest) : ResponseBuilderContext<GetLockSessionResponse> {
        val maybeSession = lockSessionService.findByShareableToken(request.shareableToken!!)

        val builder = FlatBufferBuilder(1024)
        if(maybeSession == null) {
            val messageOffset = builder.createString("Shareable token not found")
            Error.startError(builder)
            Error.addMessage(builder, messageOffset)
            val errorOffset = Error.endError(builder)
            GetLockSessionResponse.startGetLockSessionResponse(builder)
            GetLockSessionResponse.addError(builder, errorOffset)
            val getLockSessionResponseOffset = GetLockSessionResponse.endGetLockSessionResponse(builder)
            return ResponseBuilderContext(builder, getLockSessionResponseOffset)

        } else {
            val nameOffset = builder.createString(maybeSession.name)
            val publicKeyOffset = builder.createByteVector(maybeSession.decodePublicKey())
            val availableForContract = maybeSession.availableForContract

            GetLockSessionResponse.startGetLockSessionResponse(builder)
            GetLockSessionResponse.addName(builder, nameOffset)
            GetLockSessionResponse.addPublicKey(builder, publicKeyOffset)
            GetLockSessionResponse.addAvailableForContract(builder, availableForContract)
            val getLockSessionResponseOffset = GetLockSessionResponse.endGetLockSessionResponse(builder)
            return ResponseBuilderContext(builder, getLockSessionResponseOffset)
        }
    }

    fun handleCreateCommandRequest(botName: String, createCommandRequest : CreateCommandRequest) : ResponseBuilderContext<CreateCommandResponse> {
        val bot = botService.getByName(botName)

        val key = getECPublicKeyFromCompressedKeyByteArray(Base64.getDecoder().decode(bot.publicKey))
        val reencode= Base64.getEncoder().encodeToString(encodePublicKeySecp1(key))

        val authorSession = authorSessionService.getSessionForBot(reencode)

        val lockSession =
            this.lockSessionService.findByShareableToken(createCommandRequest.shareableToken!!)

        val contract = this.contractService.getByNameForAuthor(createCommandRequest.contractName!!)

        val commandBytes =
            ByteArray(createCommandRequest.commandBodyLength) { createCommandRequest.commandBody(it).toByte() }

        this.contractService.saveCommand(
            authorSession,
            lockSession!!,
            contract,
            Base64.getEncoder().encodeToString(commandBytes),
        )

        val builder = FlatBufferBuilder(1024)
        CreateCommandResponse.startCreateCommandResponse(builder)
        val offset = CreateCommandResponse.endCreateCommandResponse(builder)
        return ResponseBuilderContext(builder, offset)
    }

    fun handleCreateMessageRequest(botName : String, createMessageRequest: CreateMessageRequest) : ResponseBuilderContext<CreateMessageResponse> {
        val bot = botService.getByName(botName)

        val key = getECPublicKeyFromCompressedKeyByteArray(Base64.getDecoder().decode(bot.publicKey))
        val reencode= Base64.getEncoder().encodeToString(encodePublicKeySecp1(key))

        val authorSession = authorSessionService.getSessionForBot(reencode)

        val contract = this.contractService.getByNameForAuthor(createMessageRequest.contractName!!)
        this.contractService.addMessageForBot(createMessageRequest.message!!, bot, contract)

        val builder = FlatBufferBuilder(1024)
        CreateMessageResponse.startCreateMessageResponse(builder)
        val offset = CreateMessageResponse.endCreateMessageResponse(builder)
        return ResponseBuilderContext(builder, offset)
    }
}