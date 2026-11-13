package com.muhabbet.app.data.remote.connection

import com.muhabbet.app.data.remote.connection.ConnectionEffect.CancelRetry
import com.muhabbet.app.data.remote.connection.ConnectionEffect.CloseSocket
import com.muhabbet.app.data.remote.connection.ConnectionEffect.OpenSocket
import com.muhabbet.app.data.remote.connection.ConnectionEffect.RefreshAuth
import com.muhabbet.app.data.remote.connection.ConnectionEffect.ScheduleRetry
import com.muhabbet.app.data.remote.connection.ConnectionEffect.SessionLive
import com.muhabbet.app.data.remote.connection.ConnectionEffect.SessionLost
import com.muhabbet.app.data.remote.connection.ConnectionEffect.WatchForToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The connection transition table (#811), exhaustively: every state crossed with every event.
 *
 * The expected table below is written out by hand. A pair missing from it must be a no-op, and the
 * last test checks exactly that for every pair not listed — so adding a state or an event without
 * deciding what it does fails here rather than in a user's pocket.
 */
class ConnectionMachineTest {

    private val policy = ConnectionPolicy(baseDelayMs = 1_000, maxDelayMs = 30_000, healthyAfterMs = 10_000)

    private val states: List<ConnectionState> = listOf(
        ConnectionState.Disconnected,
        ConnectionState.WaitingForAuth,
        ConnectionState.Connecting(2),
        ConnectionState.Authenticating(2),
        ConnectionState.Live(2, sinceMs = 1_000),
        ConnectionState.Backoff(2, delayMs = 2_000)
    )

    private val events: List<ConnectionEvent> = listOf(
        ConnectionEvent.Start,
        ConnectionEvent.Stop,
        ConnectionEvent.TokenMissing,
        ConnectionEvent.TokenAvailable,
        ConnectionEvent.Opened,
        ConnectionEvent.ServerAnswered(nowMs = 5_000),
        ConnectionEvent.Lost(nowMs = 5_000),
        ConnectionEvent.RetryDue
    )

    private val expected: Map<Pair<ConnectionState, ConnectionEvent>, ConnectionTransition> = buildMap {
        val (disconnected, waiting, connecting, authenticating, live, backoff) = states
        put(disconnected to ConnectionEvent.Start, ConnectionTransition(ConnectionState.Connecting(0), listOf(OpenSocket)))

        put(waiting to ConnectionEvent.Stop, ConnectionTransition(disconnected, listOf(CloseSocket, CancelRetry)))
        put(waiting to ConnectionEvent.TokenAvailable, ConnectionTransition(ConnectionState.Connecting(0), listOf(OpenSocket)))

        put(connecting to ConnectionEvent.Stop, ConnectionTransition(disconnected, listOf(CloseSocket, CancelRetry)))
        put(connecting to ConnectionEvent.TokenMissing, ConnectionTransition(waiting, listOf(WatchForToken)))
        put(connecting to ConnectionEvent.Opened, ConnectionTransition(ConnectionState.Authenticating(2)))
        put(
            connecting to ConnectionEvent.Lost(5_000),
            ConnectionTransition(ConnectionState.Backoff(3, 4_000), listOf(CloseSocket, ScheduleRetry(4_000)))
        )

        put(authenticating to ConnectionEvent.Stop, ConnectionTransition(disconnected, listOf(SessionLost, CloseSocket, CancelRetry)))
        put(
            authenticating to ConnectionEvent.ServerAnswered(5_000),
            ConnectionTransition(ConnectionState.Live(2, 5_000), listOf(SessionLive))
        )
        put(
            authenticating to ConnectionEvent.Lost(5_000),
            ConnectionTransition(ConnectionState.Backoff(3, 4_000), listOf(SessionLost, CloseSocket, ScheduleRetry(4_000)))
        )

        put(live to ConnectionEvent.Stop, ConnectionTransition(disconnected, listOf(SessionLost, CloseSocket, CancelRetry)))
        // Live for 4 s only (since 1 000, lost at 5 000): not healthy, so the backoff keeps climbing.
        put(
            live to ConnectionEvent.Lost(5_000),
            ConnectionTransition(ConnectionState.Backoff(3, 4_000), listOf(SessionLost, CloseSocket, ScheduleRetry(4_000)))
        )

        put(backoff to ConnectionEvent.Stop, ConnectionTransition(disconnected, listOf(CloseSocket, CancelRetry)))
        put(backoff to ConnectionEvent.RetryDue, ConnectionTransition(ConnectionState.Connecting(2), listOf(OpenSocket)))
    }

    @Test
    fun should_follow_the_table_for_every_listed_pair() {
        for ((pair, transition) in expected) {
            assertEquals(transition, reduce(pair.first, pair.second, policy), "${pair.first} + ${pair.second}")
        }
    }

    @Test
    fun should_do_nothing_for_every_pair_the_table_does_not_list() {
        var checked = 0
        for (state in states) for (event in events) {
            if ((state to event) in expected) continue
            assertEquals(ConnectionTransition(state), reduce(state, event, policy), "$state + $event must be a no-op")
            checked++
        }
        assertEquals(states.size * events.size - expected.size, checked)
    }

    @Test
    fun should_restart_the_backoff_after_a_session_that_stayed_healthy() {
        val live = ConnectionState.Live(attempt = 5, sinceMs = 0)
        val next = reduce(live, ConnectionEvent.Lost(nowMs = 10_000), policy)
        assertEquals(ConnectionState.Backoff(1, 1_000), next.state)
    }

    @Test
    fun should_refresh_the_token_before_retrying_when_the_server_rejected_it() {
        val next = reduce(ConnectionState.Authenticating(0), ConnectionEvent.Lost(1, authRejected = true), policy)
        assertTrue(RefreshAuth in next.effects)
        assertTrue(next.effects.indexOf(RefreshAuth) < next.effects.indexOfFirst { it is ScheduleRetry })
    }

    @Test
    fun should_grow_the_delay_against_a_server_that_accepts_and_then_closes() {
        // Open, never answer, lose — over and over: the shape of an auth-rejecting or crash-looping
        // server. The old loop reset its counter on every open and retried at ~2 s forever.
        var state: ConnectionState = ConnectionState.Connecting(0)
        val delays = mutableListOf<Long>()
        repeat(7) {
            state = reduce(state, ConnectionEvent.Opened, policy).state
            val lost = reduce(state, ConnectionEvent.Lost(nowMs = 0), policy)
            delays += (lost.effects.single { it is ScheduleRetry } as ScheduleRetry).delayMs
            state = reduce(lost.state, ConnectionEvent.RetryDue, policy).state
        }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), delays)
    }

    private operator fun <T> List<T>.component6(): T = this[5]
}
