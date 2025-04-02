package club.subjugated.tartarus_coordinator.config

import io.moquette.BrokerConstants
import io.moquette.broker.Server
import io.moquette.broker.config.MemoryConfig
import org.springframework.stereotype.Component
import java.util.*

@Component
class MqttBroker {
    private val mqttServer: Server = Server()

    init {
        val properties = Properties().apply {
            setProperty(BrokerConstants.PORT_PROPERTY_NAME, "1883") // Default MQTT port
            setProperty(BrokerConstants.ALLOW_ANONYMOUS_PROPERTY_NAME, "true") // Allow public connections
        }

        mqttServer.startServer(MemoryConfig(properties))
        println("✅ MQTT Broker started on port 1883")
    }

    fun stop() {
        mqttServer.stopServer()
        println("🛑 MQTT Broker stopped")
    }
}