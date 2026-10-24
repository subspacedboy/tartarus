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

    private val authorSessions = ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>>()
    private val lockUserSessions = ConcurrentHashMap<String, WebSocketSession>()
    private val adminSessions = ConcurrentHashMap<String, WebSocketSession>()
    private val botSessions = ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions.add(session)

        if (session.attributes.containsKey("lockUserSession")) {
            val token: String = session.attributes["lockUserSession"].toString()
            lockUserSessions[token] = session
        }

        if (session.attributes.containsKey("authorSession")) {
            val token: String = session.attributes["authorSession"].toString()
            authorSessions.computeIfAbsent(token) { CopyOnWriteArraySet() }.add(session)
        }

        if (session.attributes.containsKey("adminSession")) {
            val token: String = session.attributes["adminSession"].toString()
            adminSessions[token] = session
        }

        if (session.attributes.containsKey("botSession")) {
            val token: String = session.attributes["botSession"].toString()
            botSessions.computeIfAbsent(token) { CopyOnWriteArraySet() }.add(session)
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
            authorSessions[token]?.remove(session)
        }

        if (session.attributes.containsKey("adminSession")) {
            val token: String = session.attributes["adminSession"].toString()
            adminSessions.remove(token)
        }

        if (session.attributes.containsKey("botSession")) {
            val token: String = session.attributes["botSession"].toString()
            botSessions[token]?.remove(session)
        }
    }

    fun sendAuthorMessage(authorName: String, message: String): Boolean {
        val targetSessions = authorSessions[authorName] ?: return false
        var sent = false
        val textMessage = TextMessage(message)
        for (ws in targetSessions) {
            if (ws.isOpen) {
                ws.sendMessage(textMessage)
                sent = true
            }
        }
        return sent
    }

    fun sendBotMessage(botName: String, ownerAuthorName: String?, message: String): Boolean {
        var delivered = false
        val textMessage = TextMessage(message)

        botSessions[botName]?.forEach { ws ->
            if (ws.isOpen) {
                ws.sendMessage(textMessage)
                delivered = true
            }
        }

        if (!ownerAuthorName.isNullOrBlank()) {
            authorSessions[ownerAuthorName]?.forEach { ws ->
                if (ws.isOpen) {
                    ws.sendMessage(textMessage)
                    delivered = true
                }
            }
        }

        return delivered
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
