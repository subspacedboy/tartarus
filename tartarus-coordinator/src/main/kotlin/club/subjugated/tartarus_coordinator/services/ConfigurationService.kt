package club.subjugated.tartarus_coordinator.services

import club.subjugated.fb.message.configuration.CoordinatorConfiguration
import club.subjugated.fb.message.configuration.Key
import club.subjugated.tartarus_coordinator.api.messages.ConfigurationMessage
import club.subjugated.tartarus_coordinator.api.messages.SafetyKeyMessage
import club.subjugated.tartarus_coordinator.util.encodePublicKeySecp1
import club.subjugated.tartarus_coordinator.util.loadECPublicKeyFromPkcs8
import com.google.flatbuffers.FlatBufferBuilder
import java.security.interfaces.ECPublicKey
import java.util.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ConfigurationService {
    @Value("\${tartarus.api_uri}") var apiUri: String = ""

    @Value("\${tartarus.ws_uri}") var wsUri: String = ""

    @Value("\${tartarus.web_uri}") var webUri: String = ""

    @Value("\${tartarus.mqtt_uri}") var mqttUri: String = ""

    @Value("\${tartarus.enable_reset_command}") var enableReset: Boolean = false

    @Autowired lateinit var safetyKeyService: SafetyKeyService

    fun getConfigurationAsMessage(): ConfigurationMessage {
        val safetyKeys = this.safetyKeyService.getAllActiveSafetyKeys()
        val configuration =
            ConfigurationMessage(
                apiUri = apiUri,
                wsUri = wsUri,
                webUri = webUri,
                mqttUri = mqttUri,
                safetyKeys = safetyKeys.map { SafetyKeyMessage.fromSafetyKey(it) },
                enableResetCommand = enableReset
            )
        return configuration
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun getConfigurationAsFB(): ByteArray {
        val builder = FlatBufferBuilder(1024)

        val safetyKeys = this.safetyKeyService.getAllActiveSafetyKeys()
        // Create keys
        val keyOffsets = mutableListOf<Int>()
        for (k in safetyKeys ?: emptyList()) {
            val nameOffset = builder.createString(k.name)

            val ecPublicKey = loadECPublicKeyFromPkcs8(k.publicKey!!)
            val publicKeyData = encodePublicKeySecp1(ecPublicKey as ECPublicKey)
            val publicKeyOffset = Key.createPublicKeyVector(builder, publicKeyData.toUByteArray())

            Key.startKey(builder)
            Key.addName(builder, nameOffset)
            Key.addPublicKey(builder, publicKeyOffset)
            val keyOffset = Key.endKey(builder)
            keyOffsets.add(keyOffset)
        }

        val keysVector =
            CoordinatorConfiguration.createSafetyKeysVector(builder, keyOffsets.toIntArray())

        // Create main configuration
        val webUriOffset = builder.createString(webUri)
        val wsUriOffset = builder.createString(wsUri)
        val mqttUriOffset = builder.createString(mqttUri)
        val apiUriOffset = builder.createString(apiUri)

        CoordinatorConfiguration.startCoordinatorConfiguration(builder)
        CoordinatorConfiguration.addWebUri(builder, webUriOffset)
        CoordinatorConfiguration.addWsUri(builder, wsUriOffset)
        CoordinatorConfiguration.addMqttUri(builder, mqttUriOffset)
        CoordinatorConfiguration.addApiUri(builder, apiUriOffset)
        CoordinatorConfiguration.addSafetyKeys(builder, keysVector)
        CoordinatorConfiguration.addEnableResetCommand(builder, enableReset)
        val configOffset = CoordinatorConfiguration.endCoordinatorConfiguration(builder)

        builder.finish(configOffset)
        return builder.sizedByteArray()
    }
}
