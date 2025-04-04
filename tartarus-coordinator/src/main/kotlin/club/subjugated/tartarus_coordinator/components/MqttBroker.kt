package club.subjugated.tartarus_coordinator.components

import club.subjugated.tartarus_coordinator.services.BotService
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
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!cli")
class CustomMqttSecurity : IAuthenticator, IAuthorizatorPolicy {
    final val internalPassword: UUID = UUID.randomUUID()
    val passAsString = internalPassword.toString()

    @Autowired
    lateinit var botService: BotService

    @PostConstruct
    fun start() {
        // Initialize security component
    }

    override fun checkValid(clientId: String?, username: String?, password: ByteArray?): Boolean {
        if(username != null) {
            if(username.startsWith("b-")) {
                val bot = botService.getByName(username)
                return true
            }
        }

        val regex = "^[a-zA-Z0-9]{6}$".toRegex()
        if (username.isNullOrEmpty()) {
            return false
        }

        val isValid = regex.matches(username)

        return isValid ||
            (username == "internal" && password.contentEquals(passAsString.encodeToByteArray()))
    }

    override fun canRead(topic: Topic?, user: String?, client: String?): Boolean {
        if (user == null) {
            return false
        }

        if (user == "internal") {
            return true
        }

        if(user.startsWith("b-")) {
            val bot = botService.getByName(user)
            return bot.canReadMqtt(topic.toString())
        }

        // Locks can read their own response channel.
        if (topic!!.toString() == "locks/${user}") {
            return true
        }

        // Their own configuration channel.
        if (topic.toString() == "configuration/${user}") {
            return true
        }

        if (topic.toString() == "firmware/${user}") {
            return true
        }

        return false
    }

    override fun canWrite(topic: Topic?, user: String?, client: String?): Boolean {
        if (user == null) {
            return false
        }

        if (user == "internal") {
            return true
        }

        if(user.startsWith("b-")) {
            val bot = botService.getByName(user)
            return bot.canWriteMqtt(topic.toString())
        }

        if(isLockUser(user)){
            if(topic!!.toString().startsWith("bots/inbox_events_b-")) {
                return true
            }

            // Locks can read write to the main response channel.
            if (topic.toString() == "locks/updates") {
                return true
            }

            if (topic.toString() == "locks/firmware") {
                return true
            }
        }

        return false
    }

    @PreDestroy
    fun stop() {
        // Cleanup security component
    }

    private fun isLockUser(username : String?) : Boolean {
        if (username.isNullOrEmpty()) {
            return false
        }
        val regex = "^[a-zA-Z0-9]{6}$".toRegex()
        return regex.matches(username)
    }
}

@Component
@Profile("!cli")
class MqttBroker(private val security: CustomMqttSecurity) {
    private val mqttServer: Server = Server()

    @Value("\${tartarus.mqtt_ws_port}") val wsPortNumber: Long = 0

    @PostConstruct
    fun start() {
        val properties =
            Properties().apply {
                setProperty("websocket_port", wsPortNumber.toString())
                setProperty("netty.mqtt.message_size", "20587") // 20KB
            }

        val config: IConfig = MemoryConfig(properties)
        val handlers: List<InterceptHandler> = emptyList()
        val sslContextCreator: ISslContextCreator? = null

        mqttServer.startServer(config, handlers, sslContextCreator, security, security)
        println("✅ MQTT Broker started on ${this.wsPortNumber}")
    }

    @PreDestroy
    fun stop() {
        mqttServer.stopServer()
        println("🛑 MQTT Broker stopped")
    }
}
