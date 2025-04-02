package club.subjugated.tartarus_coordinator.components

import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.w3c.dom.Text
import java.util.concurrent.CopyOnWriteArraySet

@Component
class BotWebSocketHandler : TextWebSocketHandler() {
    private val sessions = CopyOnWriteArraySet<WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        println("Connected: $session")
        sessions.add(session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: org.springframework.web.socket.CloseStatus) {
        sessions.remove(session)
    }

    override fun handleTextMessage(session: WebSocketSession, message : TextMessage) {
        println("Received: $message")
        session.sendMessage(TextMessage("ACK"))
    }
}