package club.subjugated.tartarus_coordinator.services

import club.subjugated.fb.message.configuration.CoordinatorConfiguration
import club.subjugated.fb.message.configuration.Key
import club.subjugated.tartarus_coordinator.api.messages.ConfigurationMessage
import club.subjugated.tartarus_coordinator.api.messages.SafetyKeyMessage
import club.subjugated.tartarus_coordinator.util.encodePublicKeySecp1
import club.subjugated.tartarus_coordinator.util.loadECPublicKeyFromPkcs8
import com.google.flatbuffers.FlatBufferBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.util.*

@Service
class ConfigurationService {
    @Value("\${tartarus.api_uri}")
    var apiUri : String = ""

    @Value("\${tartarus.web_uri}")
    var webUri : String = ""

    @Value("\${tartarus.mqtt_uri}")
    var mqttUri : String = ""

    @Autowired
    lateinit var safetyKeyService: SafetyKeyService

    fun getConfigurationAsMessage() : ConfigurationMessage {
        val safetyKeys = this.safetyKeyService.getAllActiveSafetyKeys()
        val configuration = ConfigurationMessage(
            apiUri = apiUri,
            webUri = webUri,
            mqttUri = mqttUri,
            safetyKeys = safetyKeys.map { SafetyKeyMessage.fromSafetyKey(it) }
        )
        return configuration
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun getConfigurationAsFB() : ByteArray {
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

        val keysVector = CoordinatorConfiguration.createSafetyKeysVector(builder, keyOffsets.toIntArray())

        // Create main configuration
        val webUriOffset = builder.createString("http://192.168.1.180:4200")
        val mqttUriOffset = builder.createString("ws://192.168.1.180:8080/mqtt")
        val apiUriOffset = builder.createString("ws://192.168.1.180:5002")

        CoordinatorConfiguration.startCoordinatorConfiguration(builder)
        CoordinatorConfiguration.addWebUri(builder, webUriOffset)
        CoordinatorConfiguration.addMqttUri(builder, mqttUriOffset)
        CoordinatorConfiguration.addApiUri(builder, apiUriOffset)
        CoordinatorConfiguration.addSafetyKeys(builder, keysVector)
        val configOffset = CoordinatorConfiguration.endCoordinatorConfiguration(builder)

        builder.finish(configOffset)
        return builder.sizedByteArray()
    }
}