package club.subjugated.overlord_exe.services

import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.storage.BotMapRepository
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.createEs256Jwt
import club.subjugated.overlord_exe.util.deriveBotKeyPair
import club.subjugated.overlord_exe.util.encodePublicKeySecp1
import club.subjugated.overlord_exe.util.generateECKeyPair
import club.subjugated.overlord_exe.util.generateId
import club.subjugated.overlord_exe.util.loadKeyPairFromBase64Pkcs8
import club.subjugated.overlord_exe.util.loadPrivateKeyFromPkcs8Bytes
import club.subjugated.overlord_exe.util.signDoubleSha256Raw
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.interfaces.ECPublicKey
import java.util.Base64

@Serializable
data class NewAuthorSessionRequest(
    val publicKey: String,
    val sessionToken: String,
    val signature: String,
)

@Serializable
data class AuthorSessionResponse(
    val sessionToken: String? = null,
    val name: String? = null,
)

@Serializable
data class NewBotRequest(
    val publicKey: String,
    val description: String,
    val internalName: String? = null,
    val keyVersion: Int = 1,
)

@Serializable
data class NewBotResponse(
    val name: String,
    val clearTextPassword: String? = null,
    val authorSessionName: String? = null,
)

@Service
class BotMapService(
    private val botMapRepository: BotMapRepository,
    @Value("\${overlord.coordinator}") val coordinator: String,
    @Value("\${overlord.author_private_key:}") private val authorPrivateKeyB64: String,
    private val timeSource: TimeSource,
    private val environment: Environment,
) {
    private val logger: Logger = LoggerFactory.getLogger(BotMapService::class.java)

    val authorKeyPair: KeyPair by lazy {
        if (authorPrivateKeyB64.isNotBlank()) {
            loadKeyPairFromBase64Pkcs8(authorPrivateKeyB64)
        } else {
            logger.warn("No overlord.author_private_key configured; generating ephemeral Author keypair")
            generateECKeyPair()
        }
    }

    @Volatile private var cachedAuthorSessionName: String? = null

    fun getBotMap(internalName: String): BotMap {
        return botMapRepository.findByInternalNameAndCoordinator(internalName, coordinator)!!
    }

    fun getOrCreateBotMap(internalName: String, description: String): BotMap {
        val existing = botMapRepository.findByInternalNameAndCoordinator(internalName, coordinator)
        if (existing != null) {
            return existing
        }

        return runBlocking {
            val keyPair = deriveBotKeyPair(authorKeyPair.private, internalName, 1)
            val encodedKey =
                Base64.getEncoder()
                    .encodeToString(encodePublicKeySecp1(keyPair.public as ECPublicKey))

            val newBot =
                if (environment.activeProfiles.contains("test")) {
                    NewBotResponse(name = generateId("b-"), clearTextPassword = "")
                } else {
                    try {
                        register(encodedKey, description, internalName, 1)
                    } catch (e: Exception) {
                        logger.warn(
                            "Coordinator registration failed for $internalName (${e.message}); using local placeholder until coordinator is reachable"
                        )
                        NewBotResponse(name = generateId("b-"), clearTextPassword = "")
                    }
                }

            createBotMap(
                internalName,
                newBot.name,
                newBot.clearTextPassword ?: "",
                keyPair.private.encoded,
                keyPair.public.encoded,
            )
        }
    }

    suspend fun ensureAuthorSession(): String {
        cachedAuthorSessionName?.let { return it }

        if (environment.activeProfiles.contains("test")) {
            val testSession = "as-TESTAUTHOR"
            cachedAuthorSessionName = testSession
            return testSession
        }

        val compressedAuthorPub =
            Base64.getEncoder()
                .encodeToString(encodePublicKeySecp1(authorKeyPair.public as ECPublicKey))
        val sessionToken = generateId("")
        val rawSig =
            signDoubleSha256Raw(
                authorKeyPair.private,
                sessionToken.toByteArray(StandardCharsets.UTF_8),
            )
        val sigB64 = Base64.getEncoder().encodeToString(rawSig)

        val client =
            HttpClient(OkHttp) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        try {
            val response: HttpResponse =
                client.post("$coordinator/author_sessions/") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        NewAuthorSessionRequest(
                            publicKey = compressedAuthorPub,
                            sessionToken = sessionToken,
                            signature = sigB64,
                        )
                    )
                }
            val body: AuthorSessionResponse = response.body()
            val name = body.name ?: body.sessionToken ?: error("Empty AuthorSession response")
            cachedAuthorSessionName = name
            return name
        } finally {
            client.close()
        }
    }

    suspend fun getAuthorJwt(): String {
        val authorSessionName = ensureAuthorSession()
        return createEs256Jwt(authorSessionName, authorKeyPair.private)
    }

    fun getBotJwt(botMap: BotMap): String {
        val privateKey = loadPrivateKeyFromPkcs8Bytes(botMap.privateKey!!)
        return createEs256Jwt(botMap.externalName, privateKey)
    }

    suspend fun register(
        encodedKey: String,
        description: String,
        internalName: String? = null,
        keyVersion: Int = 1,
    ): NewBotResponse {
        val authorJwt = getAuthorJwt()
        val newBotRequest =
            NewBotRequest(
                publicKey = encodedKey,
                description = description,
                internalName = internalName,
                keyVersion = keyVersion,
            )
        val client =
            HttpClient(OkHttp) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        try {
            val response: HttpResponse =
                client.post("$coordinator/bots/") {
                    header(HttpHeaders.Authorization, "Bearer $authorJwt")
                    contentType(ContentType.Application.Json)
                    setBody(newBotRequest)
                }

            println("Status: ${response.status}")
            println("Response: ${response.bodyAsText()}")

            return response.body()
        } finally {
            client.close()
        }
    }

    fun createBotMap(
        internalName: String,
        externalName: String,
        password: String,
        privateKey: ByteArray,
        publicKey: ByteArray,
    ): BotMap {
        val botMap =
            BotMap(
                internalName = internalName,
                externalName = externalName,
                coordinator = coordinator,
                password = password,
                privateKey = privateKey,
                publicKey = publicKey,
                createdAt = timeSource.nowInUtc(),
                updatedAt = timeSource.nowInUtc(),
            )

        botMapRepository.save(botMap)
        return botMap
    }
}
