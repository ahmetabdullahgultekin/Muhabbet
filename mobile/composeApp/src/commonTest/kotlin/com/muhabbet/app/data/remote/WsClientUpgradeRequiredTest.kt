package com.muhabbet.app.data.remote

import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #873: a server that refuses this build's protocol must not be retried forever — every retry is
 * refused the same way, and the user needs to be told to update rather than "reconnecting…".
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WsClientUpgradeRequiredTest {

    private val sessions = mutableListOf<FakeWebSocketSession>()

    private val apiClient = ApiClient(
        FakeTokenStorage(),
        MockEngine {
            respond("""{"data":null,"timestamp":"t"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, Application.Json.toString()))
        }
    )

    private fun wsClient(scope: CoroutineScope) = WsClient(
        apiClient = apiClient,
        tokenProvider = { "token" },
        scope = scope,
        openSession = { openFake() }
    )

    private fun openFake(): WebSocketSession = FakeWebSocketSession().also { sessions.add(it) }

    @Test
    fun should_stop_reconnecting_once_the_server_requires_an_upgrade() = runTest {
        val ws = wsClient(backgroundScope)
        ws.connect()
        runCurrent()
        assertEquals(1, sessions.size)

        sessions.single().deliver(wsJson.encodeToString<WsMessage>(WsMessage.Error(ErrorCode.CLIENT_UPGRADE_REQUIRED)))
        sessions.single().dropConnection()
        runCurrent()
        advanceTimeBy(120_000)

        assertTrue(apiClient.upgradeRequired.value)
        assertEquals(1, sessions.size, "a refused protocol must not be retried")
        assertEquals(ConnectionState.UPGRADE_REQUIRED, ws.connectionState.value)
    }

    @Test
    fun should_keep_reconnecting_after_an_ordinary_drop() = runTest {
        val ws = wsClient(backgroundScope)
        ws.connect()
        runCurrent()

        sessions.single().dropConnection()
        runCurrent()
        advanceTimeBy(60_000)

        assertTrue(sessions.size > 1, "an ordinary drop must still reconnect")
    }

    @Test
    fun should_skip_an_unknown_frame_without_dropping_the_session() = runTest {
        val ws = wsClient(backgroundScope)
        ws.connect()
        runCurrent()

        sessions.single().deliver("""{"type":"future.frame","x":1}""")
        runCurrent()

        assertEquals(1, sessions.size)
        assertEquals(ConnectionState.CONNECTED, ws.connectionState.value)
    }
}
