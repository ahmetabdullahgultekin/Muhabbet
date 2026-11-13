package com.muhabbet.app.data.remote.connection

/**
 * The WebSocket lifecycle as an explicit state machine (#811, ADR 0012).
 *
 * It used to be eight unsynchronised mutable fields — `session`, `reconnectAttempt`,
 * `shouldReconnect`, `heartbeatJob`, `connectJob`, `generation`, … — written from the caller's
 * thread and read from the loop's, with each new edge case (#511, #521, #667) patched by one more
 * flag and one more comment. Here every state names exactly what the client is doing, [reduce] is
 * the whole transition table and is pure, and the only side effects are the [ConnectionEffect]s it
 * returns, which one actor in `WsClient` executes in order.
 */
sealed interface ConnectionState {
    /** Not wanted: logged out, or never started. */
    data object Disconnected : ConnectionState

    /** Wanted, but there is no access token to open a socket with. Makes no network calls. */
    data object WaitingForAuth : ConnectionState

    /** Opening the socket. [attempt] is the count of consecutive failures so far. */
    data class Connecting(val attempt: Int) : ConnectionState

    /**
     * The socket is open and a probe is out, but the server has not yet shown it accepted the
     * token. The server answers a bad token by sending an error and closing *after* the upgrade, so
     * an open socket alone proves nothing — treating it as connected is what let an auth-rejecting
     * server be retried at the minimum delay forever.
     */
    data class Authenticating(val attempt: Int) : ConnectionState

    /** The server has answered on this socket. [sinceMs] is when it first did. */
    data class Live(val attempt: Int, val sinceMs: Long) : ConnectionState

    /** Waiting [delayMs] before trying again. [attempt] is the failure that led here. */
    data class Backoff(val attempt: Int, val delayMs: Long) : ConnectionState
}

/** Whether frames can be sent and are being received. */
val ConnectionState.isLive: Boolean get() = this is ConnectionState.Live

/** Whether the client means to hold a connection, live or not. */
val ConnectionState.isWanted: Boolean get() = this != ConnectionState.Disconnected

sealed interface ConnectionEvent {
    data object Start : ConnectionEvent
    data object Stop : ConnectionEvent
    data object TokenMissing : ConnectionEvent
    data object TokenAvailable : ConnectionEvent
    data object Opened : ConnectionEvent

    /** The first inbound frame on an open socket: the server took the token. */
    data class ServerAnswered(val nowMs: Long) : ConnectionEvent

    /**
     * The socket closed, failed to open, or stopped answering (a missed pong — #802).
     * [authRejected] when the server said the token was invalid before closing.
     */
    data class Lost(val nowMs: Long, val authRejected: Boolean = false) : ConnectionEvent

    data object RetryDue : ConnectionEvent
}

sealed interface ConnectionEffect {
    data object OpenSocket : ConnectionEffect
    data object CloseSocket : ConnectionEffect
    data object WatchForToken : ConnectionEffect
    data class ScheduleRetry(val delayMs: Long) : ConnectionEffect
    data object CancelRetry : ConnectionEffect

    /** The server has accepted this socket: drain the outbox, restate focus, fill the gap. */
    data object SessionLive : ConnectionEffect

    /** A socket that was carrying traffic is gone: in-flight sends go back to the queue. */
    data object SessionLost : ConnectionEffect

    /** The server rejected the access token: refresh it explicitly before the next attempt. */
    data object RefreshAuth : ConnectionEffect
}

data class ConnectionTransition(val state: ConnectionState, val effects: List<ConnectionEffect> = emptyList())

/**
 * Backoff and health limits. [jitter] maps a nominal delay to the one actually used; tests pass
 * the identity, production spreads reconnects so a server restart is not met by every client at
 * the same instant.
 */
data class ConnectionPolicy(
    val baseDelayMs: Long = 1_000,
    val maxDelayMs: Long = 30_000,
    /** A Live session must last this long before a later drop starts the backoff from scratch. */
    val healthyAfterMs: Long = 10_000,
    val jitter: (Long) -> Long = { it }
) {
    /** 1 s, 2 s, 4 s … capped at [maxDelayMs], for failure number [attempt] (1-based). */
    fun delayFor(attempt: Int): Long {
        val shift = (attempt - 1).coerceIn(0, MAX_SHIFT)
        return jitter((baseDelayMs shl shift).coerceAtMost(maxDelayMs))
    }

    private companion object {
        const val MAX_SHIFT = 20
    }
}

/** The whole transition table. Anything not listed leaves the state alone and does nothing. */
fun reduce(state: ConnectionState, event: ConnectionEvent, policy: ConnectionPolicy): ConnectionTransition {
    if (event == ConnectionEvent.Stop) {
        return when (state) {
            ConnectionState.Disconnected -> ConnectionTransition(state)
            is ConnectionState.Authenticating, is ConnectionState.Live -> ConnectionTransition(
                ConnectionState.Disconnected,
                listOf(ConnectionEffect.SessionLost, ConnectionEffect.CloseSocket, ConnectionEffect.CancelRetry)
            )
            else -> ConnectionTransition(
                ConnectionState.Disconnected,
                listOf(ConnectionEffect.CloseSocket, ConnectionEffect.CancelRetry)
            )
        }
    }
    return when (state) {
        ConnectionState.Disconnected -> when (event) {
            ConnectionEvent.Start -> ConnectionTransition(ConnectionState.Connecting(0), listOf(ConnectionEffect.OpenSocket))
            else -> ConnectionTransition(state)
        }
        ConnectionState.WaitingForAuth -> when (event) {
            ConnectionEvent.TokenAvailable ->
                ConnectionTransition(ConnectionState.Connecting(0), listOf(ConnectionEffect.OpenSocket))
            else -> ConnectionTransition(state)
        }
        is ConnectionState.Connecting -> when (event) {
            ConnectionEvent.TokenMissing ->
                ConnectionTransition(ConnectionState.WaitingForAuth, listOf(ConnectionEffect.WatchForToken))
            ConnectionEvent.Opened -> ConnectionTransition(ConnectionState.Authenticating(state.attempt))
            is ConnectionEvent.Lost -> backoff(state.attempt, event, wasCarryingTraffic = false, policy)
            else -> ConnectionTransition(state)
        }
        is ConnectionState.Authenticating -> when (event) {
            is ConnectionEvent.ServerAnswered -> ConnectionTransition(
                ConnectionState.Live(state.attempt, event.nowMs),
                listOf(ConnectionEffect.SessionLive)
            )
            is ConnectionEvent.Lost -> backoff(state.attempt, event, wasCarryingTraffic = true, policy)
            else -> ConnectionTransition(state)
        }
        is ConnectionState.Live -> when (event) {
            is ConnectionEvent.Lost -> {
                // A session that stayed healthy has earned a fresh start; one that dropped straight
                // away (accept-then-close: an auth reject, a crash-looping server) keeps climbing.
                val healthy = event.nowMs - state.sinceMs >= policy.healthyAfterMs
                backoff(if (healthy) 0 else state.attempt, event, wasCarryingTraffic = true, policy)
            }
            else -> ConnectionTransition(state)
        }
        is ConnectionState.Backoff -> when (event) {
            ConnectionEvent.RetryDue ->
                ConnectionTransition(ConnectionState.Connecting(state.attempt), listOf(ConnectionEffect.OpenSocket))
            else -> ConnectionTransition(state)
        }
    }
}

private fun backoff(
    attempt: Int,
    event: ConnectionEvent.Lost,
    wasCarryingTraffic: Boolean,
    policy: ConnectionPolicy
): ConnectionTransition {
    val next = attempt + 1
    val delay = policy.delayFor(next)
    val effects = buildList {
        if (wasCarryingTraffic) add(ConnectionEffect.SessionLost)
        add(ConnectionEffect.CloseSocket)
        if (event.authRejected) add(ConnectionEffect.RefreshAuth)
        add(ConnectionEffect.ScheduleRetry(delay))
    }
    return ConnectionTransition(ConnectionState.Backoff(next, delay), effects)
}
