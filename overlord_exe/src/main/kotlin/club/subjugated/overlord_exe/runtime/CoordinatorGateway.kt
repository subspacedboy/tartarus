package club.subjugated.overlord_exe.runtime

import club.subjugated.overlord_exe.bots.general.BotCreateCommandPayload
import club.subjugated.overlord_exe.bots.general.BotCreateContractPayload
import club.subjugated.overlord_exe.bots.general.BotCreateMessagePayload
import club.subjugated.overlord_exe.services.AuthorSessionResponse
import club.subjugated.overlord_exe.services.BotContractInfo
import club.subjugated.overlord_exe.services.NewAuthorSessionRequest
import club.subjugated.overlord_exe.services.NewBotRequest
import club.subjugated.overlord_exe.services.NewBotResponse
import club.subjugated.overlord_exe.util.generateId
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Serializable
data class CoordinatorBotDto(
    val name: String,
    val publicKey: String? = null,
    val description: String? = null,
    val state: String? = null,
    val internalName: String? = null,
    val keyVersion: Int = 1,
)

data class RecordedBotCommand(
    val botName: String,
    val contractName: String,
    val shareableToken: String?,
    val signedMessageBase64: String,
)

data class RecordedBotMessage(
    val botName: String,
    val contractName: String,
    val message: String,
)

/**
 * Gateway for all HTTP interactions with the Tartarus Coordinator.
 */
interface CoordinatorGateway {
    fun createAuthorSession(request: NewAuthorSessionRequest): AuthorSessionResponse
    fun getMyBots(authorJwt: String): List<CoordinatorBotDto>
    fun registerBot(authorJwt: String, request: NewBotRequest): NewBotResponse

    fun createContract(
        botName: String,
        botJwt: String,
        shareableToken: String,
        signedMessageBase64: String,
        serialNumber: Int = 0,
    ): BotContractInfo

    fun getContractBySession(
        botName: String,
        jwt: String,
        lockSession: String,
        serial: Int,
    ): BotContractInfo

    fun createCommand(
        botName: String,
        botJwt: String,
        contractName: String,
        shareableToken: String?,
        signedMessageBase64: String,
    )

    fun addMessage(
        botName: String,
        botJwt: String,
        contractName: String,
        message: String,
    )
}

@Service
class KtorCoordinatorGateway(
    @Value("\${overlord.coordinator}") private val coordinator: String,
    private val environment: Environment,
) : CoordinatorGateway {
    private val logger: Logger = LoggerFactory.getLogger(KtorCoordinatorGateway::class.java)

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(jsonParser)
        }
    }

    // In-memory state used when running in "test" profile so full code paths execute without network calls
    val testContractsBySerial = ConcurrentHashMap<String, BotContractInfo>()
    val recordedCommands = CopyOnWriteArrayList<RecordedBotCommand>()
    val recordedMessages = CopyOnWriteArrayList<RecordedBotMessage>()

    private fun isTestProfile(): Boolean = environment.activeProfiles.contains("test")

    fun resetTestState() {
        testContractsBySerial.clear()
        recordedCommands.clear()
        recordedMessages.clear()
    }

    @PreDestroy
    fun close() {
        try {
            httpClient.close()
        } catch (e: Exception) {
            logger.debug("Error closing shared Ktor HttpClient: ${e.message}")
        }
    }

    override fun createAuthorSession(request: NewAuthorSessionRequest): AuthorSessionResponse {
        if (isTestProfile()) {
            return AuthorSessionResponse(sessionToken = "as-TESTAUTHOR", name = "as-TESTAUTHOR")
        }
        return runBlocking {
            val response = httpClient.post("$coordinator/author_sessions/") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException(
                    "Failed to create author session: HTTP ${response.status} - ${response.bodyAsText()}"
                )
            }
            response.body()
        }
    }

    override fun getMyBots(authorJwt: String): List<CoordinatorBotDto> {
        if (isTestProfile()) {
            return emptyList()
        }
        return runBlocking {
            val response = httpClient.get("$coordinator/bots/mine") {
                header(HttpHeaders.Authorization, "Bearer $authorJwt")
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException(
                    "Failed to fetch author bots: HTTP ${response.status} - ${response.bodyAsText()}"
                )
            }
            response.body()
        }
    }

    override fun registerBot(authorJwt: String, request: NewBotRequest): NewBotResponse {
        if (isTestProfile()) {
            return NewBotResponse(name = generateId("b-"), clearTextPassword = "")
        }
        return runBlocking {
            val response = httpClient.post("$coordinator/bots/") {
                header(HttpHeaders.Authorization, "Bearer $authorJwt")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException(
                    "Failed to register bot ${request.internalName}: HTTP ${response.status} - ${response.bodyAsText()}"
                )
            }
            response.body()
        }
    }

    override fun createContract(
        botName: String,
        botJwt: String,
        shareableToken: String,
        signedMessageBase64: String,
        serialNumber: Int,
    ): BotContractInfo {
        if (isTestProfile()) {
            val info = BotContractInfo(
                name = generateId("c-"),
                shareableToken = shareableToken,
                state = "CREATED",
                body = signedMessageBase64,
                nextCounter = 1,
                serialNumber = serialNumber,
                lockSessionToken = "ls-TEST",
            )
            testContractsBySerial["$botName:$serialNumber"] = info
            return info
        }
        return runBlocking {
            val response = httpClient.post("$coordinator/bots/$botName/contracts") {
                header(HttpHeaders.Authorization, "Bearer $botJwt")
                contentType(ContentType.Application.Json)
                setBody(
                    BotCreateContractPayload(
                        shareableToken = shareableToken,
                        signedMessageBase64 = signedMessageBase64,
                    )
                )
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException(
                    "Failed to create contract for $botName: HTTP ${response.status} - ${response.bodyAsText()}"
                )
            }
            try {
                response.body<BotContractInfo>()
            } catch (_: Exception) {
                BotContractInfo(
                    name = generateId("c-"),
                    shareableToken = shareableToken,
                    state = "CREATED",
                    body = signedMessageBase64,
                    nextCounter = 1,
                    serialNumber = serialNumber,
                )
            }
        }
    }

    override fun getContractBySession(
        botName: String,
        jwt: String,
        lockSession: String,
        serial: Int,
    ): BotContractInfo {
        if (isTestProfile()) {
            return testContractsBySerial["$botName:$serial"]
                ?: BotContractInfo(
                    name = generateId("c-"),
                    state = "ACCEPTED",
                    nextCounter = 1,
                    serialNumber = serial,
                    lockSessionToken = lockSession,
                )
        }
        return runBlocking {
            val response = httpClient.get("$coordinator/bots/$botName/contracts/bySession/$lockSession/$serial") {
                header(HttpHeaders.Authorization, "Bearer $jwt")
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException(
                    "Failed to fetch contract for $botName ($lockSession/$serial): HTTP ${response.status} - ${response.bodyAsText()}"
                )
            }
            response.body()
        }
    }

    override fun createCommand(
        botName: String,
        botJwt: String,
        contractName: String,
        shareableToken: String?,
        signedMessageBase64: String,
    ) {
        if (isTestProfile()) {
            recordedCommands.add(
                RecordedBotCommand(
                    botName = botName,
                    contractName = contractName,
                    shareableToken = shareableToken,
                    signedMessageBase64 = signedMessageBase64,
                )
            )
            return
        }
        runBlocking {
            val response = httpClient.post("$coordinator/bots/$botName/commands") {
                header(HttpHeaders.Authorization, "Bearer $botJwt")
                contentType(ContentType.Application.Json)
                setBody(
                    BotCreateCommandPayload(
                        contractName = contractName,
                        shareableToken = shareableToken,
                        signedMessageBase64 = signedMessageBase64,
                    )
                )
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException(
                    "Failed to create command for $botName on $contractName: HTTP ${response.status} - ${response.bodyAsText()}"
                )
            }
        }
    }

    override fun addMessage(
        botName: String,
        botJwt: String,
        contractName: String,
        message: String,
    ) {
        if (isTestProfile()) {
            recordedMessages.add(
                RecordedBotMessage(
                    botName = botName,
                    contractName = contractName,
                    message = message,
                )
            )
            return
        }
        runBlocking {
            val response = httpClient.post("$coordinator/bots/$botName/contracts/$contractName/messages") {
                header(HttpHeaders.Authorization, "Bearer $botJwt")
                contentType(ContentType.Application.Json)
                setBody(BotCreateMessagePayload(message = message))
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException(
                    "Failed to add message for $botName on $contractName: HTTP ${response.status} - ${response.bodyAsText()}"
                )
            }
        }
    }
}
