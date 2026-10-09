package club.subjugated.tartarus_coordinator.filters

import club.subjugated.tartarus_coordinator.services.AdminSessionService
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
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
) : HandshakeInterceptor {
    private val logger = LoggerFactory.getLogger(JwtHandshakeInterceptor::class.java)

    override fun beforeHandshake(
        request: org.springframework.http.server.ServerHttpRequest,
        response: org.springframework.http.server.ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        if (request is ServletServerHttpRequest) {
            val servletRequest: HttpServletRequest = request.servletRequest
            val lockUserJwt = servletRequest.getParameter("lockUserSessionJwt")
            val authorJwt = servletRequest.getParameter("authorJwt")
            val adminJwt = servletRequest.getParameter("adminJwt")
            var somethingWasValidated = false

            if (!lockUserJwt.isNullOrEmpty()) {
                when (
                    val result =
                        verifyJwt(authorSessionService, lockUserSessionService, lockUserJwt, adminSessionService)
                ) {
                    is JwtValidationResult.LockUserSession -> {
                        attributes["lockUserSession"] = result.value
                        somethingWasValidated = true
                    }
                    else -> {}
                }
            }

            if (!authorJwt.isNullOrEmpty()) {
                when (
                    val result = verifyJwt(authorSessionService, lockUserSessionService, authorJwt, adminSessionService)
                ) {
                    is JwtValidationResult.AuthorSession -> {
                        attributes["authorSession"] = result.value
                        somethingWasValidated = true
                    }
                    else -> {}
                }
            }

            if (!adminJwt.isNullOrEmpty()) {
                when (
                    val result = verifyJwt(authorSessionService, lockUserSessionService, adminJwt, adminSessionService)
                ) {
                    is JwtValidationResult.AdminSession -> {
                        attributes["adminSession"] = result.value
                        somethingWasValidated = true
                    }
                    else -> {}
                }
            }

            if (somethingWasValidated) {
                logger.info("WebSocket handshake accepted with attributes: {}", attributes.keys)
            } else {
                logger.warn(
                    "WebSocket handshake rejected: no valid credentials provided. lockUserJwt present: {}, authorJwt present: {}, adminJwt present: {}",
                    !lockUserJwt.isNullOrEmpty(),
                    !authorJwt.isNullOrEmpty(),
                    !adminJwt.isNullOrEmpty(),
                )
            }

            return somethingWasValidated
        }
        return false
    }

    override fun afterHandshake(
        request: org.springframework.http.server.ServerHttpRequest,
        response: org.springframework.http.server.ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) {}
}
