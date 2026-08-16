package com.muhabbet.messaging.adapter.`in`.websocket

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.PingMessage
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
class WebSocketSessionManager {

    companion object {
        /**
         * How long a session may go without a single inbound frame before we stop believing it has
         * a peer. The mobile client sends [com.muhabbet.shared.protocol.WsMessage.Ping] every 30 s
         * (`WsClient.kt`), and a live socket also answers our server-initiated ping frames, so this
         * is three consecutive missed heartbeats — long enough never to fire on a healthy
         * connection, short enough that a phone which walked into a lift is not still "online"
         * minutes later. Keep this a multiple of the client's interval: if one moves, so does this.
         */
        private const val STALE_THRESHOLD_MS = 90_000L

        /**
         * Worst-case staleness is THRESHOLD + INTERVAL, because a session can cross the threshold
         * the instant after a sweep. At 30 s that ceiling is 2 minutes; the old 2-minute sweep
         * would have made it 3.5. The sweep only walks a map of open sockets and writes one ping
         * frame each, so running it four times as often costs nothing measurable.
         */
        private const val REAP_INTERVAL_SECONDS = 30L
    }

    private val log = LoggerFactory.getLogger(javaClass)

    // userId -> set of sessions (a user can have multiple devices)
    private val sessions = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()

    // sessionId -> userId (reverse lookup)
    private val sessionToUser = ConcurrentHashMap<String, UUID>()

    // sessionId -> epoch millis of the last inbound frame from that peer.
    // `WebSocketSession.isOpen` cannot answer "is the peer still there" — it stays true for as long
    // as the OS believes the TCP connection exists, which is minutes after a phone loses its
    // network. Only a frame we actually received proves liveness, so we record when one arrived.
    private val lastSeenAt = ConcurrentHashMap<String, Long>()

    // Periodic reaping of sessions that are closed, silent, or unwritable
    private val cleanupExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ws-session-cleanup").apply { isDaemon = true }
    }

    @PostConstruct
    fun startCleanup() {
        cleanupExecutor.scheduleAtFixedRate({
            try {
                reapStaleSessions()
            } catch (e: Exception) {
                log.warn("WS session cleanup error: {}", e.message)
            }
        }, REAP_INTERVAL_SECONDS, REAP_INTERVAL_SECONDS, TimeUnit.SECONDS)
    }

    @PreDestroy
    fun stopCleanup() {
        cleanupExecutor.shutdownNow()
    }

    fun register(userId: UUID, session: WebSocketSession) {
        sessions.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(session)
        sessionToUser[session.id] = userId
        lastSeenAt[session.id] = System.currentTimeMillis()
        log.info("WebSocket registered: userId={}, sessionId={}, total={}", userId, session.id, sessions.size)
    }

    fun unregister(session: WebSocketSession) {
        val userId = sessionToUser[session.id] ?: return
        forget(userId, session)
        log.info("WebSocket unregistered: userId={}, sessionId={}", userId, session.id)
    }

    /**
     * Records that a frame just arrived on this session. Called for **every** inbound frame, not
     * only `Ping`: any traffic at all proves the peer is reachable, and a chatty client should not
     * be reaped just because its heartbeat happened to be due.
     *
     * @param now injectable so a test can age sessions without sleeping.
     */
    fun touch(session: WebSocketSession, now: Long = System.currentTimeMillis()) {
        // computeIfPresent rather than put: a frame on a session we never registered, or have
        // already reaped, must not resurrect a map entry that nothing will ever clean up.
        lastSeenAt.computeIfPresent(session.id) { _, _ -> now }
    }

    fun getUserId(session: WebSocketSession): UUID? = sessionToUser[session.id]

    fun isOnline(userId: UUID): Boolean = sessions[userId]?.isNotEmpty() == true

    fun sendToUser(userId: UUID, message: String) {
        val userSessions = sessions[userId] ?: return
        val stale = mutableListOf<WebSocketSession>()
        userSessions.forEach { session ->
            if (session.isOpen) {
                try {
                    synchronized(session) { session.sendMessage(TextMessage(message)) }
                } catch (e: Exception) {
                    log.warn("Failed to send WS message to session {}, marking stale: {}", session.id, e.message)
                    stale.add(session)
                }
            } else {
                stale.add(session)
            }
        }
        // Clean up stale sessions immediately
        stale.forEach { forget(userId, it) }
    }

    fun getOnlineUserCount(): Int = sessions.size

    /**
     * Closes and unregisters every session that can no longer be shown to have a peer: already
     * closed, silent for longer than [STALE_THRESHOLD_MS], or refusing a ping frame.
     *
     * The ping is what makes this work while the client is asleep and not sending its own
     * heartbeat — a write to a socket whose peer is gone fails far sooner than a read times out.
     *
     * @param now injectable so a test can age sessions without sleeping.
     */
    fun reapStaleSessions(now: Long = System.currentTimeMillis()) {
        var reaped = 0
        // ConcurrentHashMap iteration is weakly consistent and holds no locks, so removing entries
        // (here, via forget) from inside the traversal is safe — including the re-entrant unregister
        // that session.close() triggers through the handler's afterConnectionClosed.
        sessions.forEach { (userId, sessionSet) ->
            sessionSet.forEach { session ->
                val idleMs = now - (lastSeenAt[session.id] ?: now)
                val reason = when {
                    !session.isOpen -> "socket already closed"
                    idleMs > STALE_THRESHOLD_MS -> "no inbound frame for ${idleMs}ms"
                    !ping(session) -> "ping frame could not be written"
                    else -> null
                }
                if (reason != null) {
                    log.info(
                        "Reaping WebSocket session: userId={}, sessionId={}, idleMs={}, reason={}",
                        userId, session.id, idleMs, reason
                    )
                    // Close first: a graceful close lets the handler's afterConnectionClosed run the
                    // presence cleanup (offline in Redis, last_seen_at, OFFLINE broadcast). forget()
                    // afterwards is the belt-and-braces for when that callback never fires.
                    closeQuietly(session)
                    forget(userId, session)
                    reaped++
                }
            }
        }
        if (reaped > 0) log.info("Reaped {} dead WebSocket sessions", reaped)
    }

    /** Removes a session from all three maps. Idempotent. */
    private fun forget(userId: UUID, session: WebSocketSession) {
        sessionToUser.remove(session.id)
        lastSeenAt.remove(session.id)
        // computeIfPresent keeps "remove the session" and "drop the now-empty user entry" atomic
        // against a concurrent register() for the same user, which a check-then-remove would lose.
        sessions.computeIfPresent(userId) { _, set ->
            set.remove(session)
            if (set.isEmpty()) null else set
        }
    }

    /** @return false if the ping could not be written, which means the session is dead. */
    private fun ping(session: WebSocketSession): Boolean = try {
        synchronized(session) { session.sendMessage(PingMessage()) }
        true
    } catch (e: Exception) {
        log.debug("Ping failed for session {}: {}", session.id, e.message)
        false
    }

    private fun closeQuietly(session: WebSocketSession) {
        try {
            if (session.isOpen) session.close(CloseStatus.GOING_AWAY)
        } catch (e: Exception) {
            log.debug("Failed to close dead session {}: {}", session.id, e.message)
        }
    }
}
