package club.subjugated.overlord_exe.services

import club.subjugated.overlord_exe.models.BotMap
import club.subjugated.overlord_exe.storage.BotMapRepository
import club.subjugated.overlord_exe.util.TimeSource
import club.subjugated.overlord_exe.util.encodePublicKeySecp1
import club.subjugated.overlord_exe.util.generateECKeyPair
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.interfaces.ECPublicKey
import java.util.Base64

@Serializable
data class NewBotRequest(val publicKey: String, val description: String)

@Serializable
data class NewBotResponse(val name : String, val clearTextPassword: String)

@Service
class BotMapService(
    private val botMapRepository: BotMapRepository,
    @Value("\${overlord.coordinator}") val coordinator : String,
    private val timeSource: TimeSource
) {

    fun getBotMap(internalName: String) : BotMap {
        return botMapRepository.findByInternalNameAndCoordinator(internalName, coordinator)!!
    }

    fun getOrCreateBotMap(internalName : String, description: String) : BotMap {
        val botMap = botMapRepository.findByInternalNameAndCoordinator(internalName, coordinator) ?: run {
            runBlocking {
                val keyPair = generateECKeyPair()
                val encodedKey = Base64.getEncoder().encodeToString(encodePublicKeySecp1(keyPair.public as ECPublicKey))

                val newBot = register(encodedKey, description)
                createBotMap(internalName, newBot.name, newBot.clearTextPassword, keyPair.private.encoded, keyPair.public.encoded)
            }
        }

        return botMap
    }

    suspend fun register(encodedKey: String, description: String) : NewBotResponse {
        val newBotRequest = NewBotRequest(encodedKey, description)
        val client = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val response: HttpResponse = client.post("$coordinator/bots/") {
            contentType(ContentType.Application.Json)
            setBody(newBotRequest)
        }

        println("Status: ${response.status}")
        println("Response: ${response.bodyAsText()}")

        client.close()
        return response.body()
    }

    fun createBotMap(internalName : String, externalName : String, password: String, privateKey: ByteArray, publicKey: ByteArray) : BotMap {
        val botMap = BotMap(
            internalName = internalName,
            externalName = externalName,
            coordinator = coordinator,
            password = password,
            privateKey = privateKey,
            publicKey = publicKey,
            createdAt = timeSource.nowInUtc(),
            updatedAt = timeSource.nowInUtc()
        )

        botMapRepository.save(botMap)
        return botMap
    }
}