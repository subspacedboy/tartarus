package club.subjugated.tartarus_coordinator.filters

import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.LockUserSessionService
import club.subjugated.tartarus_coordinator.util.JwtValidationResult
import club.subjugated.tartarus_coordinator.util.verifyJwt
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

class JwtHandshakeInterceptor(
    private val authorSessionService: AuthorSessionService,
    private val lockUserSessionService: LockUserSessionService
) : HandshakeInterceptor {
    override fun beforeHandshake(
        request: org.springframework.http.server.ServerHttpRequest,
        response: org.springframework.http.server.ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        if (request is ServletServerHttpRequest) {
            val servletRequest: HttpServletRequest = request.servletRequest
            val lockUserJwt = servletRequest.getParameter("lockUserSessionJwt")
            val authorJwt = servletRequest.getParameter("authorJwt")
            var somethingWasValidated = false

            if(!lockUserJwt.isNullOrEmpty()) {
                when(val result = verifyJwt(authorSessionService, lockUserSessionService, lockUserJwt)) {
                    is JwtValidationResult.AuthorSession -> {}
                    JwtValidationResult.Invalid -> {}
                    is JwtValidationResult.LockUserSession -> {
                        attributes["lockUserSession"] = result.value
                        somethingWasValidated = true
                    }
                }
            }

            if(!authorJwt.isNullOrEmpty()) {
                when(val result = verifyJwt(authorSessionService, lockUserSessionService, authorJwt)) {
                    is JwtValidationResult.AuthorSession -> {
                        attributes["authorSession"] = result.value
                        somethingWasValidated = true
                    }
                    JwtValidationResult.Invalid -> {}
                    is JwtValidationResult.LockUserSession -> {}
                }
            }

            return somethingWasValidated
        }
        return false
    }

    override fun afterHandshake(
        request: org.springframework.http.server.ServerHttpRequest,
        response: org.springframework.http.server.ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?
    ) {
    }
}