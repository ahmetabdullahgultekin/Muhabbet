package com.muhabbet.messaging.adapter.`in`.websocket

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
class WebSocketSessionManager {

    private val log = LoggerFactory.getLogger(javaClass)

    // userId -> set of sessions (a user can have multiple devices)
    private val sessions = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()

    // sessionId -> userId (reverse lookup)
    private val sessionToUser = ConcurrentHashMap<String, UUID>()

    // Periodic cleanup of orphaned (closed but not unregistered) sessions
    private val cleanupExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ws-session-cleanup").apply { isDaemon = true }
    }

    @PostConstruct
    fun startCleanup() {
        cleanupExecutor.scheduleAtFixedRate({
            try {
                var cleaned = 0
                sessions.forEach { (userId, sessionSet) ->
                    val removed = sessionSet.removeIf { !it.isOpen }
                    if (removed) cleaned++
                    if (sessionSet.isEmpty()) sessions.remove(userId)
                }
                if (cleaned > 0) log.info("Cleaned {} orphaned WebSocket sessions", cleaned)
            } catch (e: Exception) {
                log.warn("WS session cleanup error: {}", e.message)
            }
        }, 2, 2, TimeUnit.MINUTES)
    }

    @PreDestroy
    fun stopCleanup() {
        cleanupExecutor.shutdownNow()
    }

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
        val userSessions = sessions[userId] ?: return
        val stale = mutableListOf<WebSocketSession>()
        userSessions.forEach { session ->
            if (session.isOpen) {
                try {
                    session.sendMessage(TextMessage(message))
                } catch (e: Exception) {
                    log.warn("Failed to send WS message to session {}, marking stale: {}", session.id, e.message)
                    stale.add(session)
                }
            } else {
                stale.add(session)
            }
        }
        // Clean up stale sessions immediately
        if (stale.isNotEmpty()) {
            stale.forEach { s ->
                userSessions.remove(s)
                sessionToUser.remove(s.id)
            }
            if (userSessions.isEmpty()) sessions.remove(userId)
        }
    }

    fun getOnlineUserCount(): Int = sessions.size
}
