package com.muhabbet.messaging.adapter.`in`.websocket

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.PingMessage
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
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

        /**
         * How long one write to a socket may take before that socket is the problem.
         *
         * Tomcat's own blocking send timeout is twenty seconds and nothing here overrides it, so
         * without a limit of our own a single peer whose TCP window is full holds the writing
         * thread for twenty. Ten seconds is deliberately under that: when a peer is this far
         * behind, the decorator closes it and the writer moves on, rather than Tomcat deciding for
         * us at twice the delay.
         */
        private const val SEND_TIME_LIMIT_MS = 10_000

        /**
         * How much may pile up for a peer that is not draining. Eight of the largest frame the
         * server will accept (`WebSocketConfig.MAX_MESSAGE_BUFFER` is 64 KB). Past this the peer is
         * not slow, it is gone, and holding more of its backlog only costs this instance memory.
         */
        private const val SEND_BUFFER_LIMIT_BYTES = 8 * 64 * 1024

        /** Close code for "your token is no longer valid; refresh and reconnect" (ICD §3.1). */
        const val CLOSE_CODE_TOKEN_EXPIRED = 4401

        /**
         * Close code for "this session was revoked" — logout, device removal, account deletion
         * (ICD §3.1). Distinct from [CLOSE_CODE_TOKEN_EXPIRED] because the right client reaction is
         * different: a refresh fixes an expiry and cannot fix a revocation.
         */
        const val CLOSE_CODE_SESSION_REVOKED = 4403

        val TOKEN_EXPIRED: CloseStatus = CloseStatus(CLOSE_CODE_TOKEN_EXPIRED, "TOKEN_EXPIRED")
    }

    private val log = LoggerFactory.getLogger(javaClass)

    // userId -> set of sessions (a user can have multiple devices)
    private val sessions = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()

    // sessionId -> the decorated session every writer must go through.
    //
    // The raw WebSocketSession Spring hands a handler callback is NOT safe to write to from two
    // threads, and this application has at least three writers: the container thread handling a
    // frame, the fan-out thread delivering someone else's message, and the reaper writing pings.
    // Tomcat answers an overlap with IllegalStateException [TEXT_FULL_WRITING]. Before #490 the
    // manager took `synchronized(session)` and ChatWebSocketHandler wrote to the same sessions in
    // seven places without it — a monitor only half the writers take protects nothing — and when
    // the manager's write was the one that lost, its catch marked a perfectly healthy session stale
    // and dropped it from every map, so a user with an open socket vanished from presence and from
    // every later broadcast.
    //
    // ConcurrentWebSocketSessionDecorator serialises sends properly and bounds them, so the answer
    // is to have exactly one wrapper per socket and let nothing write to the raw session. This map
    // is what lets a handler holding the raw session reach its wrapper: the decorator delegates
    // getId(), so every existing id-keyed map keeps working unchanged.
    private val byId = ConcurrentHashMap<String, WebSocketSession>()

    // sessionId -> userId (reverse lookup)
    private val sessionToUser = ConcurrentHashMap<String, UUID>()

    // sessionId -> epoch millis of the last inbound frame from that peer.
    // `WebSocketSession.isOpen` cannot answer "is the peer still there" — it stays true for as long
    // as the OS believes the TCP connection exists, which is minutes after a phone loses its
    // network. Only a frame we actually received proves liveness, so we record when one arrived.
    private val lastSeenAt = ConcurrentHashMap<String, Long>()

    // userId -> the conversation currently on screen and foregrounded, per WsMessage.ConversationFocus.
    // Absent means "none" — background, no chat open, or never reported (an old client build).
    // Keyed per-user, not per-session: multi-device linked sessions (MultiDeviceConfig) is default
    // OFF, so a second concurrent device for one user is not a case this needs to get right yet.
    private val activeConversation = ConcurrentHashMap<UUID, UUID>()

    // sessionId -> the device the session's token was issued to, and the reverse index, so that
    // revoking one device closes that device's sockets and nobody else's (#747). A session
    // registered without a device (none today, but the parameter is optional) is simply absent.
    private val sessionToDevice = ConcurrentHashMap<String, UUID>()
    private val deviceSessions = ConcurrentHashMap<UUID, MutableSet<String>>()

    // sessionId -> epoch millis at which the token that opened it expires, and the one-shot task
    // that will close it then. The token is checked once, at the handshake; without this a socket
    // opened with a fifteen-minute token stayed authenticated for as long as it stayed open (#747).
    private val sessionExpiresAt = ConcurrentHashMap<String, Long>()
    private val expiryTasks = ConcurrentHashMap<String, ScheduledFuture<*>>()

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

    /**
     * @param deviceId the token's device, indexed so [closeForDevice] can find this session.
     * @param expiresAt the token's `exp`. The session is closed with [TOKEN_EXPIRED] at that instant
     *   by a one-shot task, and the periodic sweep closes it too should that task ever be missed.
     */
    fun register(userId: UUID, session: WebSocketSession, deviceId: UUID? = null, expiresAt: Instant? = null) {
        val tracked = ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES)
        sessions.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(tracked)
        byId[session.id] = tracked
        sessionToUser[session.id] = userId
        lastSeenAt[session.id] = System.currentTimeMillis()
        if (deviceId != null) {
            sessionToDevice[session.id] = deviceId
            deviceSessions.computeIfAbsent(deviceId) { ConcurrentHashMap.newKeySet() }.add(session.id)
        }
        if (expiresAt != null) scheduleExpiry(session.id, expiresAt.toEpochMilli())
        log.info("WebSocket registered: userId={}, sessionId={}, total={}", userId, session.id, sessions.size)
    }

    /**
     * Closes every open session of [userId] with [status] and forgets them. Used when the account
     * itself stops being allowed to hold one (#747).
     *
     * @return how many sessions were closed.
     */
    fun closeForUser(userId: UUID, status: CloseStatus): Int {
        val open = sessions[userId]?.toList().orEmpty()
        open.forEach { closeAndForget(userId, it, status) }
        if (open.isNotEmpty()) log.info("Closed {} WebSocket session(s) for user {}: {}", open.size, userId, status)
        return open.size
    }

    /**
     * Closes every open session authenticated as [deviceId] with [status] and forgets them. The
     * user's other devices keep their sockets.
     *
     * @return how many sessions were closed.
     */
    fun closeForDevice(deviceId: UUID, status: CloseStatus): Int {
        val ids = deviceSessions[deviceId]?.toList().orEmpty()
        var closed = 0
        ids.forEach { id ->
            val userId = sessionToUser[id] ?: return@forEach
            val tracked = byId[id] ?: return@forEach
            closeAndForget(userId, tracked, status)
            closed++
        }
        if (closed > 0) log.info("Closed {} WebSocket session(s) for device {}: {}", closed, deviceId, status)
        return closed
    }

    /**
     * Closes every session whose token has expired by [now]. The precise close is the one-shot task
     * [register] schedules; this is the sweep's backstop for one that was lost.
     *
     * @param now injectable so a test can expire tokens without sleeping.
     * @return how many sessions were closed.
     */
    fun closeExpiredSessions(now: Long = System.currentTimeMillis()): Int {
        val expired = sessionExpiresAt.filterValues { it <= now }.keys
        return expired.count { expire(it) }
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

    /**
     * Records — or clears, for a `null` [conversationId] — which conversation [userId] is currently
     * looking at. Never throws on a bad frame; the caller decodes the id before calling this.
     */
    fun setActiveConversation(userId: UUID, conversationId: UUID?) {
        if (conversationId == null) activeConversation.remove(userId) else activeConversation[userId] = conversationId
    }

    /**
     * Whether [userId] is foregrounded on [conversationId] right now, per the last
     * `WsMessage.ConversationFocus` it sent. This is the check a push send must consult instead of
     * [isOnline] (#618) — a live socket says a device is reachable, not that this particular
     * conversation is the one on screen.
     */
    fun isViewingConversation(userId: UUID, conversationId: UUID): Boolean =
        activeConversation[userId] == conversationId

    /**
     * Writes one frame to one session — the reply path, for an ack, an error or a pong that answers
     * the frame a handler is holding.
     *
     * Takes the raw session a handler was given and finds its decorator, so a handler never has to
     * know one exists. A session that is not registered yet is written to directly: the only two
     * such writes are the auth failures in `afterConnectionEstablished`, which happen before
     * register() and therefore before any other thread can possibly know about this socket.
     *
     * Logs rather than throws. A failed ack is not worth killing the connection over, and the
     * reaper will collect a socket that has genuinely died within its next sweep. Unlike
     * [sendToUser] it does not forget the session, because this is a reply on the peer's own
     * thread, not evidence about the peer's health that a broadcast has.
     */
    fun send(session: WebSocketSession, payload: String) {
        val target = byId[session.id] ?: session
        try {
            target.sendMessage(TextMessage(payload))
        } catch (e: Exception) {
            log.warn("Failed to write reply to session {}: {}", session.id, e.message)
        }
    }

    fun sendToUser(userId: UUID, message: String) {
        val userSessions = sessions[userId] ?: return
        val stale = mutableListOf<WebSocketSession>()
        userSessions.forEach { session ->
            if (session.isOpen) {
                try {
                    // No synchronized(): the set holds decorators, which serialise their own sends.
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
        closeExpiredSessions(now)
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

    /** Removes a session from every map and cancels its expiry task. Idempotent. */
    private fun forget(userId: UUID, session: WebSocketSession) {
        // Resolved through byId because callers reach here with either instance: the reaper walks
        // the set and so holds the decorator, while unregister() is handed the raw session by the
        // container. Removing the wrong one would leave the set holding a closed socket forever.
        val tracked = byId.remove(session.id) ?: session
        sessionToUser.remove(session.id)
        lastSeenAt.remove(session.id)
        sessionExpiresAt.remove(session.id)
        expiryTasks.remove(session.id)?.cancel(false)
        sessionToDevice.remove(session.id)?.let { deviceId ->
            deviceSessions.computeIfPresent(deviceId) { _, ids ->
                ids.remove(session.id)
                if (ids.isEmpty()) null else ids
            }
        }
        // computeIfPresent keeps "remove the session" and "drop the now-empty user entry" atomic
        // against a concurrent register() for the same user, which a check-then-remove would lose.
        sessions.computeIfPresent(userId) { _, set ->
            set.remove(tracked)
            if (set.isEmpty()) null else set
        }
        // Only once every session for this user is gone — a second device may still be looking at
        // something. Not "the socket that just closed happened to be the one that last reported
        // focus," because with one entry per user there is no way to tell whose report it was.
        if (!isOnline(userId)) activeConversation.remove(userId)
    }

    private fun scheduleExpiry(sessionId: String, expiresAtMs: Long) {
        sessionExpiresAt[sessionId] = expiresAtMs
        val delayMs = (expiresAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
        expiryTasks[sessionId] = cleanupExecutor.schedule({
            try {
                expire(sessionId)
            } catch (e: Exception) {
                log.warn("WS token-expiry close failed for session {}: {}", sessionId, e.message)
            }
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    /** Closes one session because its token expired. @return false if it was already gone. */
    private fun expire(sessionId: String): Boolean {
        val userId = sessionToUser[sessionId] ?: return false
        val tracked = byId[sessionId] ?: return false
        log.info("Closing WebSocket session at token expiry: userId={}, sessionId={}", userId, sessionId)
        closeAndForget(userId, tracked, TOKEN_EXPIRED)
        return true
    }

    private fun closeAndForget(userId: UUID, session: WebSocketSession, status: CloseStatus) {
        // Close first so the handler's afterConnectionClosed runs the presence cleanup, exactly as
        // the reaper does; forget() afterwards in case that callback never arrives.
        try {
            if (session.isOpen) session.close(status)
        } catch (e: Exception) {
            log.debug("Failed to close session {} ({}): {}", session.id, status, e.message)
        }
        forget(userId, session)
    }

    /** @return false if the ping could not be written, which means the session is dead. */
    private fun ping(session: WebSocketSession): Boolean = try {
        // Decorated, so this queues behind any in-flight send instead of colliding with it, and is
        // bounded by SEND_TIME_LIMIT_MS instead of Tomcat's twenty seconds — which matters here
        // more than anywhere, because the sweep is a single thread visiting every open socket in
        // turn and one unresponsive peer used to stop it reaching the rest.
        session.sendMessage(PingMessage())
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
