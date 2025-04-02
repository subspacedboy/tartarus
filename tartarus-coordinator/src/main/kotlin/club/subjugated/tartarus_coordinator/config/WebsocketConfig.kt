package club.subjugated.tartarus_coordinator.config

import club.subjugated.tartarus_coordinator.components.BotWebSocketHandler
import club.subjugated.tartarus_coordinator.components.EventWebSocketHandler
import club.subjugated.tartarus_coordinator.filters.JwtHandshakeInterceptor
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.LockUserSessionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebsocketConfig(
    private val webSocketHandler: EventWebSocketHandler,
    private val botWebSocketHandler: BotWebSocketHandler) : WebSocketConfigurer {

    @Autowired
    lateinit var lockUserSessionService: LockUserSessionService
    @Autowired
    lateinit var authorSessionService: AuthorSessionService

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(webSocketHandler, "/ws/events")
            .addInterceptors(JwtHandshakeInterceptor(authorSessionService, lockUserSessionService))
            .setAllowedOrigins("*")

        registry.addHandler(botWebSocketHandler, "/ws/bots").setAllowedOrigins("*")
    }
}