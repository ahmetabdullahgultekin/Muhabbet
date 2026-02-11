package com.muhabbet.messaging.adapter.`in`.websocket

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class WebSocketSessionManager {

    private val log = LoggerFactory.getLogger(javaClass)

    // userId -> set of sessions (a user can have multiple devices)
    private val sessions = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()

    // sessionId -> userId (reverse lookup)
    private val sessionToUser = ConcurrentHashMap<String, UUID>()

    fun register(userId: UUID, session: WebSocketSession) {
        sessions.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(session)
        sessionToUser[session.id] = userId
        log.info("WebSocket registered: userId={}, sessionId={}, total={}", userId, session.id, sessions.size)
    }

    fun unregister(session: WebSocketSession) {
        val userId = sessionToUser.remove(session.id) ?: return
        sessions[userId]?.remove(session)
        if (sessions[userId]?.isEmpty() == true) {
            sessions.remove(userId)
        }
        log.info("WebSocket unregistered: userId={}, sessionId={}", userId, session.id)
    }

    fun getUserId(session: WebSocketSession): UUID? = sessionToUser[session.id]

    fun isOnline(userId: UUID): Boolean = sessions[userId]?.isNotEmpty() == true

    fun sendToUser(userId: UUID, message: String) {
        sessions[userId]?.forEach { session ->
            if (session.isOpen) {
                try {
                    session.sendMessage(TextMessage(message))
                } catch (e: Exception) {
                    log.warn("Failed to send WS message to session {}: {}", session.id, e.message)
                }
            }
        }
    }

    fun getOnlineUserCount(): Int = sessions.size
}
