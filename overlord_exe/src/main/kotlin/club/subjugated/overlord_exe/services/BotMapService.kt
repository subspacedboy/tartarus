package club.subjugated.overlord_exe.services

import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.runtime.CoordinatorBotDto
import club.subjugated.overlord_exe.runtime.CoordinatorGateway
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
import kotlinx.serialization.Serializable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.interfaces.ECPublicKey
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

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
    private val coordinatorGateway: CoordinatorGateway,
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
    private val botCache = ConcurrentHashMap<String, BotMap>()

    fun findBotMap(internalName: String): BotMap? {
        botCache[internalName]?.let { return it }
        return botMapRepository.findByInternalNameAndCoordinator(internalName, coordinator)?.also {
            botCache[internalName] = it
        }
    }

    fun getBotMap(internalName: String): BotMap {
        return findBotMap(internalName)
            ?: throw IllegalStateException("No BotMap registered for internalName='$internalName' at coordinator='$coordinator'")
    }

    fun getOrCreateBotMap(
        internalName: String,
        description: String,
        preferredKeyVersion: Int? = null,
    ): BotMap {
        val existing = botMapRepository.findByInternalNameAndCoordinator(internalName, coordinator)

        val remoteBots: List<CoordinatorBotDto> = try {
            if (environment.activeProfiles.contains("test")) {
                emptyList()
            } else {
                coordinatorGateway.getMyBots(getAuthorJwt())
            }
        } catch (e: Exception) {
            logger.debug("Could not fetch remote bots from coordinator (${e.message})")
            emptyList()
        }

        val remoteMatch = remoteBots.find { it.internalName == internalName }
        val keyVersion = preferredKeyVersion ?: remoteMatch?.keyVersion ?: 1

        val keyPair = deriveBotKeyPair(authorKeyPair.private, internalName, keyVersion)
        val encodedKey = Base64.getEncoder()
            .encodeToString(encodePublicKeySecp1(keyPair.public as ECPublicKey))

        val newBot = if (environment.activeProfiles.contains("test")) {
            NewBotResponse(
                name = existing?.externalName ?: remoteMatch?.name ?: generateId("b-"),
                clearTextPassword = existing?.password ?: "",
            )
        } else {
            try {
                register(encodedKey, description, internalName, keyVersion)
            } catch (e: Exception) {
                logger.warn(
                    "Coordinator registration failed for $internalName (${e.message}); using local placeholder until coordinator is reachable"
                )
                NewBotResponse(
                    name = remoteMatch?.name ?: existing?.externalName ?: generateId("b-"),
                    clearTextPassword = existing?.password ?: "",
                )
            }
        }

        val saved = if (existing != null) {
            existing.externalName = newBot.name
            existing.password = newBot.clearTextPassword ?: ""
            existing.privateKey = keyPair.private.encoded
            existing.publicKey = keyPair.public.encoded
            existing.updatedAt = timeSource.nowInUtc()
            botMapRepository.save(existing)
        } else {
            createBotMap(
                internalName = internalName,
                externalName = newBot.name,
                password = newBot.clearTextPassword ?: "",
                privateKey = keyPair.private.encoded,
                publicKey = keyPair.public.encoded,
            )
        }

        botCache[internalName] = saved
        return saved
    }

    fun ensureAuthorSession(): String {
        cachedAuthorSessionName?.let { return it }

        if (environment.activeProfiles.contains("test")) {
            val testSession = "as-TESTAUTHOR"
            cachedAuthorSessionName = testSession
            return testSession
        }

        val compressedAuthorPub = Base64.getEncoder()
            .encodeToString(encodePublicKeySecp1(authorKeyPair.public as ECPublicKey))
        val sessionToken = generateId("")
        val rawSig = signDoubleSha256Raw(
            authorKeyPair.private,
            sessionToken.toByteArray(StandardCharsets.UTF_8),
        )
        val sigB64 = Base64.getEncoder().encodeToString(rawSig)

        val body = coordinatorGateway.createAuthorSession(
            NewAuthorSessionRequest(
                publicKey = compressedAuthorPub,
                sessionToken = sessionToken,
                signature = sigB64,
            )
        )
        val name = body.name ?: body.sessionToken ?: error("Empty AuthorSession response")
        cachedAuthorSessionName = name
        return name
    }

    fun getAuthorJwt(): String {
        val authorSessionName = ensureAuthorSession()
        return createEs256Jwt(authorSessionName, authorKeyPair.private)
    }

    fun getBotJwt(botMap: BotMap): String {
        val privateKey = loadPrivateKeyFromPkcs8Bytes(botMap.privateKey!!)
        return createEs256Jwt(botMap.externalName, privateKey)
    }

    fun register(
        encodedKey: String,
        description: String,
        internalName: String? = null,
        keyVersion: Int = 1,
    ): NewBotResponse {
        val authorJwt = getAuthorJwt()
        val newBotRequest = NewBotRequest(
            publicKey = encodedKey,
            description = description,
            internalName = internalName,
            keyVersion = keyVersion,
        )
        return coordinatorGateway.registerBot(authorJwt, newBotRequest)
    }

    fun createBotMap(
        internalName: String,
        externalName: String,
        password: String,
        privateKey: ByteArray,
        publicKey: ByteArray,
    ): BotMap {
        val botMap = BotMap(
            internalName = internalName,
            externalName = externalName,
            coordinator = coordinator,
            password = password,
            privateKey = privateKey,
            publicKey = publicKey,
            createdAt = timeSource.nowInUtc(),
            updatedAt = timeSource.nowInUtc(),
        )

        val saved = botMapRepository.save(botMap)
        botCache[internalName] = saved
        return saved
    }
}
