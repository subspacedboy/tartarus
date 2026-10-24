package club.subjugated.tartarus_coordinator.filters

import club.subjugated.tartarus_coordinator.services.AdminSessionService
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.BotService
import club.subjugated.tartarus_coordinator.services.LockUserSessionService
import club.subjugated.tartarus_coordinator.util.JwtValidationResult
import club.subjugated.tartarus_coordinator.util.verifyJwt
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

class JwtHandshakeInterceptor(
    private val authorSessionService: AuthorSessionService,
    private val lockUserSessionService: LockUserSessionService,
    private val adminSessionService: AdminSessionService? = null,
    private val botService: BotService? = null,
) : HandshakeInterceptor {
    private val logger = LoggerFactory.getLogger(JwtHandshakeInterceptor::class.java)

    override fun beforeHandshake(
        request: org.springframework.http.server.ServerHttpRequest,
        response: org.springframework.http.server.ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        if (request !is ServletServerHttpRequest) {
            return false
        }
        val servletRequest: HttpServletRequest = request.servletRequest
        val validated =
            validateParam(servletRequest, "lockUserSessionJwt", attributes) ||
                validateParam(servletRequest, "authorJwt", attributes) ||
                validateParam(servletRequest, "adminJwt", attributes) ||
                validateParam(servletRequest, "botJwt", attributes)

        if (validated) {
            logger.info("WebSocket handshake accepted with attributes: {}", attributes.keys)
        } else {
            logger.warn("WebSocket handshake rejected: no valid credentials provided.")
        }
        return validated
    }

    private fun validateParam(
        servletRequest: HttpServletRequest,
        paramName: String,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val jwt = servletRequest.getParameter(paramName)
        if (jwt.isNullOrEmpty()) {
            return false
        }
        return when (
            val result =
                verifyJwt(
                    authorSessionService,
                    lockUserSessionService,
                    jwt,
                    adminSessionService,
                    botService,
                )
        ) {
            is JwtValidationResult.LockUserSession -> {
                attributes["lockUserSession"] = result.value
                true
            }
            is JwtValidationResult.AuthorSession -> {
                attributes["authorSession"] = result.value
                true
            }
            is JwtValidationResult.AdminSession -> {
                attributes["adminSession"] = result.value
                true
            }
            is JwtValidationResult.BotSession -> {
                attributes["botSession"] = result.value
                true
            }
            else -> false
        }
    }

    override fun afterHandshake(
        request: org.springframework.http.server.ServerHttpRequest,
        response: org.springframework.http.server.ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) {}
}
