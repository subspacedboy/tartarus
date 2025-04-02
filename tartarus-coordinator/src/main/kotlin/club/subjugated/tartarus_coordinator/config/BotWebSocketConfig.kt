package club.subjugated.tartarus_coordinator.config

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
class BotWebSocketConfig : WebSocketMessageBrokerConfigurer {
    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")  // Enables topic-based messaging
        registry.setApplicationDestinationPrefixes("/app")  // Prefix for incoming messages
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws/bot2").setAllowedOrigins("*").withSockJS()  // WebSocket endpoint
    }
}