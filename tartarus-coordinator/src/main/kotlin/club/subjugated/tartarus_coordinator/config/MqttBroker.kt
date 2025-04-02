package club.subjugated.tartarus_coordinator.config

import io.moquette.BrokerConstants
import io.moquette.broker.ISslContextCreator
import io.moquette.broker.Server
import io.moquette.broker.config.IConfig
import io.moquette.broker.config.MemoryConfig
import io.moquette.broker.security.IAuthenticator
import io.moquette.broker.security.IAuthorizatorPolicy
import io.moquette.broker.subscriptions.Topic
import io.moquette.interception.InterceptHandler
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.springframework.stereotype.Component
import java.util.*

@Component
class CustomMqttSecurity : IAuthenticator, IAuthorizatorPolicy {

    private val userCredentials = mapOf("DUJ7SD" to "maybe?", "internal" to "password")

    private val canReadAuthorizedTopics = mapOf("DUJ7SD" to setOf("locks/DUJ7SD"), "internal" to setOf("locks/updates", "locks/DUJ7SD", "devices/status"))
    private val canWriteAuthorizedTopics = mapOf("DUJ7SD" to setOf("locks/updates", "locks/DUJ7SD", "devices/status"), "internal" to setOf("locks/updates", "locks/DUJ7SD", "devices/status"))

    @PostConstruct
    fun start() {
        // Initialize security component
    }

    override fun checkValid(clientId: String?, username: String?, password: ByteArray?): Boolean {
        return username != null && password != null &&
                userCredentials[username] == password.decodeToString()
    }

    override fun canRead(topic: Topic?, user: String?, client: String?): Boolean {
        return user != null && topic.toString() in canReadAuthorizedTopics[user].orEmpty()
    }

    override fun canWrite(topic: Topic?, user: String?, client: String?): Boolean {
        return user != null && topic.toString() in canWriteAuthorizedTopics[user].orEmpty()
    }

    @PreDestroy
    fun stop() {
        // Cleanup security component
    }
}

@Component
class MqttBroker(private val security: CustomMqttSecurity) {
    private val mqttServer: Server = Server()

    init {
        val properties = Properties().apply {
        }

        val config: IConfig = MemoryConfig(properties)
        val handlers: List<InterceptHandler> = emptyList()
        val sslContextCreator: ISslContextCreator? = null

        mqttServer.startServer(config, handlers, sslContextCreator, security, security)
        println("✅ MQTT Broker started on port 1883")
    }

    fun stop() {
        mqttServer.stopServer()
        println("🛑 MQTT Broker stopped")
    }
}