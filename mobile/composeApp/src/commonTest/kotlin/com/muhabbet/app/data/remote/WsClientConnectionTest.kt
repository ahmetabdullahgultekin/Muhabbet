package com.muhabbet.app.data.remote

import com.muhabbet.app.data.remote.connection.ConnectionState
import com.muhabbet.app.data.remote.connection.isLive
import com.muhabbet.shared.protocol.WsMessage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The connection lifecycle (#811), liveness (#802) and the generation guard (#511, #521), driven
 * through the real client on virtual time against scripted sockets.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WsClientConnectionTest {

    @Test
    fun should_go_live_only_once_the_server_has_answered() = runTest {
        val h = WsClientHarness(this, serverAnswers = false)
        h.client.connect()
        runCurrent()

        assertIs<ConnectionState.Authenticating>(h.client.connectionState.value, "an open socket proves nothing yet")

        h.sessions.single().serverSends(WsMessage.Pong)
        runCurrent()
        assertTrue(h.client.connectionState.value.isLive)
    }

    @Test
    fun should_keep_a_connection_when_a_replaced_composition_disconnects_late() = runTest {
        val h = WsClientHarness(this)
        val dying = h.client.connect()
        h.client.connect()
        h.client.disconnect(dying)
        runCurrent()

        assertTrue(h.client.connectionState.value.isLive, "a stale teardown must not close the new connection (#511)")
        assertEquals(1, h.sessions.size)
    }

    @Test
    fun should_tear_down_when_the_live_generation_disconnects() = runTest {
        val h = WsClientHarness(this)
        val live = h.client.connect()
        runCurrent()

        h.client.disconnect(live)
        runCurrent()

        assertEquals(ConnectionState.Disconnected, h.client.connectionState.value)
        assertEquals(0, h.openSessions)
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(1, h.sessions.size, "nothing reconnects after a real teardown")
    }

    @Test
    fun should_reuse_a_live_connection_rather_than_open_a_rival_or_misreport_it() = runTest {
        val h = WsClientHarness(this)
        h.client.connect()
        runCurrent()

        h.client.connect()
        runCurrent()

        assertEquals(1, h.sessions.size)
        assertTrue(h.client.connectionState.value.isLive, "a second connect() must not claim Connecting (#521)")
    }

    @Test
    fun should_never_hold_two_sockets_however_connect_and_disconnect_interleave() = runTest {
        val h = WsClientHarness(this)
        var generation = h.client.connect()
        repeat(20) { round ->
            if (round % 3 == 0) h.client.disconnect(generation)
            generation = h.client.connect()
            if (round % 2 == 0) runCurrent()
        }
        runCurrent()

        assertTrue(h.openSessions <= 1, "open sockets: ${h.openSessions}")
        assertTrue(h.client.connectionState.value.isLive)
    }

    @Test
    fun should_make_no_network_calls_while_waiting_for_a_token_and_connect_once_one_appears() = runTest {
        val h = WsClientHarness(this, token = null)
        h.client.connect()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(ConnectionState.WaitingForAuth, h.client.connectionState.value)
        assertEquals(0, h.httpRequests)
        assertTrue(h.sessions.isEmpty())

        h.token = "fresh"
        advanceTimeBy(2_001)
        runCurrent()
        assertTrue(h.client.connectionState.value.isLive)
    }

    @Test
    fun should_back_off_further_each_time_a_server_accepts_and_closes_and_never_poke_users_me() = runTest {
        val h = WsClientHarness(this)
        // Accept, then close before saying anything: the shape of a rejected token or a server in a
        // crash loop. The old client reset its counter on every open and retried at ~2 s forever.
        h.onOpen = { it.answerPings = false; it.dropConnection() }
        h.client.connect()
        advanceTimeBy(1_000 + 2_000 + 4_000 + 8_000 + 16_000 + 30_000 + 1)
        runCurrent()

        val gaps = h.openedAt.zipWithNext { a, b -> b - a }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L), gaps)
        assertEquals(0, h.httpRequests, "the client must not fire GET /users/me to provoke a refresh")
    }

    @Test
    fun should_refresh_the_token_explicitly_when_the_server_rejects_it() = runTest {
        val h = WsClientHarness(this)
        h.onOpen = {
            it.answerPings = false
            it.serverSends(WsMessage.Error(code = "AUTH_TOKEN_INVALID", message = "expired"))
            it.dropConnection()
        }
        h.client.connect()
        runCurrent()

        assertEquals(1, h.refreshes)
        assertIs<ConnectionState.Backoff>(h.client.connectionState.value)
    }

    @Test
    fun should_abandon_a_half_open_socket_and_open_a_new_one() = runTest {
        // Writes succeed and nothing ever comes back: NAT rebinding, a Wi-Fi to LTE handover (#802).
        val h = WsClientHarness(this, serverAnswers = true)
        h.client.connect()
        runCurrent()
        assertTrue(h.client.connectionState.value.isLive)

        h.sessions.single().answerPings = false
        h.serverAnswers = true
        advanceTimeBy(WsClient.PING_INTERVAL_MS + WsClient.PONG_TIMEOUT_MS + 1)
        runCurrent()
        assertFalse(h.client.connectionState.value.isLive, "a ping with no answer must end Live")

        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(2, h.sessions.size, "a new socket replaces the dead one")
        assertTrue(h.client.connectionState.value.isLive)
        assertTrue(h.sessions[0].isDropped, "the dead socket is closed, not leaked")
    }

    @Test
    fun should_keep_a_socket_that_answers_its_pings() = runTest {
        val h = WsClientHarness(this)
        h.client.connect()
        advanceTimeBy(10 * 60_000)
        runCurrent()

        assertEquals(1, h.sessions.size)
        assertTrue(h.client.connectionState.value.isLive)
        val pings = h.sessions.single().writtenText().count { it.contains("\"ping\"") }
        assertTrue(pings in 20..30, "about one ping per quiet interval, got $pings")
    }

    @Test
    fun should_not_ping_a_line_that_is_busy() = runTest {
        val h = WsClientHarness(this)
        h.client.connect()
        runCurrent()
        val socket = h.sessions.single()
        val probe = socket.writtenText().count { it.contains("\"ping\"") }

        repeat(12) {
            advanceTimeBy(5_000)
            socket.serverSends(WsMessage.PresenceUpdate(userId = "peer", status = com.muhabbet.shared.model.PresenceStatus.ONLINE))
            runCurrent()
        }

        assertEquals(probe, socket.writtenText().count { it.contains("\"ping\"") }, "any inbound frame is proof of life")
    }

    @Test
    fun should_keep_pong_inside_the_client() = runTest {
        val h = WsClientHarness(this)
        val seen = mutableListOf<WsMessage>()
        backgroundScope.launch { h.client.incoming.collect { seen += it } }
        h.client.connect()
        advanceTimeBy(5 * 60_000)
        runCurrent()

        assertTrue(seen.none { it == WsMessage.Pong }, "Pong is a liveness signal, not a UI event")
    }

    @Test
    fun should_deliver_a_delete_for_a_message_that_arrived_live() = runTest {
        val h = WsClientHarness(this)
        val seen = mutableListOf<WsMessage>()
        backgroundScope.launch { h.client.incoming.collect { seen += it } }
        h.client.connect()
        runCurrent()
        val socket = h.sessions.single()

        socket.serverSends(
            WsMessage.NewMessage("m1", "c1", "peer", null, "hi", com.muhabbet.shared.model.ContentType.TEXT, serverTimestamp = 1)
        )
        socket.serverSends(WsMessage.MessageDeleted(messageId = "m1", conversationId = "c1", deletedBy = "peer", timestamp = 2))
        runCurrent()

        // Both used the bare messageId as their de-duplication key, so the delete was dropped as a
        // "duplicate" of the message it deleted.
        assertTrue(seen.any { it is WsMessage.MessageDeleted }, "delete-for-everyone must reach the chat")
    }
}
