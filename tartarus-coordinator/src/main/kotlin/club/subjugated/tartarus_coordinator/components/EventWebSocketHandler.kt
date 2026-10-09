package club.subjugated.tartarus_coordinator.components

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class EventWebSocketHandler : TextWebSocketHandler() {
    private val sessions = CopyOnWriteArraySet<WebSocketSession>()

    private val authorSessions = ConcurrentHashMap<String, WebSocketSession>()
    private val lockUserSessions = ConcurrentHashMap<String, WebSocketSession>()
    private val adminSessions = ConcurrentHashMap<String, WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions.add(session)

        if (session.attributes.containsKey("lockUserSession")) {
            val token: String = session.attributes["lockUserSession"].toString()
            lockUserSessions[token] = session
        }

        if (session.attributes.containsKey("authorSession")) {
            val token: String = session.attributes["authorSession"].toString()
            authorSessions[token] = session
        }

        if (session.attributes.containsKey("adminSession")) {
            val token: String = session.attributes["adminSession"].toString()
            adminSessions[token] = session
        }
    }

    override fun afterConnectionClosed(
        session: WebSocketSession,
        status: org.springframework.web.socket.CloseStatus,
    ) {
        sessions.remove(session)

        if (session.attributes.containsKey("lockUserSession")) {
            val token: String = session.attributes["lockUserSession"].toString()
            lockUserSessions.remove(token)
        }

        if (session.attributes.containsKey("authorSession")) {
            val token: String = session.attributes["authorSession"].toString()
            authorSessions.remove(token)
        }

        if (session.attributes.containsKey("adminSession")) {
            val token: String = session.attributes["adminSession"].toString()
            adminSessions.remove(token)
        }
    }

    fun sendAuthorMessage(authorName: String, message: String) {
        authorSessions[authorName]?.sendMessage(TextMessage(message))
    }

    fun sendLockUserMessage(lockUserName: String, message: String) {
        lockUserSessions[lockUserName]?.sendMessage(TextMessage(message))
    }

    fun sendAdminMessage(adminName: String, message: String) {
        adminSessions[adminName]?.sendMessage(TextMessage(message))
    }

    fun broadcastAdminMessage(message: String) {
        val textMessage = TextMessage(message)
        for (session in adminSessions.values) {
            session.sendMessage(textMessage)
        }
    }
}
