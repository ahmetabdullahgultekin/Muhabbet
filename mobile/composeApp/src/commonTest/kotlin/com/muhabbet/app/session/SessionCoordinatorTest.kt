package com.muhabbet.app.session

import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.data.remote.ConnectionState
import com.muhabbet.app.data.remote.WsClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The session's side effects follow the session, not the screen (#813). Driven in virtual time so
 * "the socket stopped polling" is an assertion about the next ten minutes, not a hope.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionCoordinatorTest {

    private val storage = FakeTokenStorage()
    private val wiringLog = mutableListOf<String>()
    private val pushRegistrations = mutableListOf<String>()

    /** How often the socket has looked for a token — the 2 s WAITING_FOR_AUTH poll of #813. */
    private var tokenReads = 0

    private fun wsClient(scope: CoroutineScope) = WsClient(
        apiClient = ApiClient(
            FakeTokenStorage(),
            MockEngine {
                respond("""{"data":null}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, Application.Json.toString()))
            }
        ),
        // Never a token, so the loop parks in WAITING_FOR_AUTH — the exact state that used to
        // outlive an expired session forever — and no test here touches the network.
        tokenProvider = { tokenReads++; null },
        scope = scope
    )

    private fun TestScope.coordinator(session: SessionManager, ws: WsClient) = SessionCoordinator(
        sessionManager = session,
        wsClient = ws,
        sessionWiring = SessionWiring(
            tokenStorage = storage,
            backgroundSync = object : BackgroundSync {
                override fun schedule() { wiringLog += "schedule" }
                override fun cancel() { wiringLog += "cancel" }
            },
            crashIdentity = object : CrashIdentity {
                override fun attach(userId: String) { wiringLog += "attach:$userId" }
                override fun detach() { wiringLog += "detach" }
            }
        ),
        registerPushToken = { tag -> pushRegistrations += tag },
        registerE2EKeys = {},
        scope = backgroundScope
    )

    @Test
    fun should_stop_the_socket_loop_for_good_when_the_session_expires() = runTest {
        storage.saveTokens("t", "r", "a", "d")
        val session = testSessionManager(storage)
        val ws = wsClient(backgroundScope)
        val coordinator = coordinator(session, ws).apply { start(); attachHost() }
        runCurrent()
        assertEquals(ConnectionState.WAITING_FOR_AUTH, ws.connectionState.value)

        session.onRefreshRejected()
        runCurrent()
        val readsAtExpiry = tokenReads
        advanceTimeBy(10 * 60 * 1_000L)

        assertEquals(readsAtExpiry, tokenReads, "no WAITING_FOR_AUTH poll may outlive the session")
        assertEquals(ConnectionState.DISCONNECTED, ws.connectionState.value)
        assertTrue(ws.connectLoopForTest == null || ws.connectLoopForTest?.isActive == false)
        coordinator.detachHost()
    }

    @Test
    fun should_forget_the_previous_accounts_focus_when_the_session_ends() = runTest {
        storage.saveTokens("t", "r", "a", "d")
        val session = testSessionManager(storage)
        val ws = wsClient(backgroundScope)
        coordinator(session, ws).apply { start(); attachHost() }
        runCurrent()
        ws.setConversationFocus("conversation-of-a")

        session.logout()
        runCurrent()

        assertNull(ws.focusedConversationForTest)
        assertTrue("detach" in wiringLog && "cancel" in wiringLog)
    }

    @Test
    fun should_not_connect_without_a_ui_host_and_should_connect_once_one_attaches() = runTest {
        storage.saveTokens("t", "r", "a", "d")
        val session = testSessionManager(storage)
        val ws = wsClient(backgroundScope)
        val coordinator = coordinator(session, ws).apply { start() }
        runCurrent()
        assertEquals(ConnectionState.DISCONNECTED, ws.connectionState.value)

        coordinator.attachHost()
        runCurrent()

        assertEquals(ConnectionState.WAITING_FOR_AUTH, ws.connectionState.value)
    }

    @Test
    fun should_keep_the_socket_through_an_activity_recreation_in_either_order() = runTest {
        storage.saveTokens("t", "r", "a", "d")
        val session = testSessionManager(storage)
        val ws = wsClient(backgroundScope)
        val coordinator = coordinator(session, ws).apply { start(); attachHost() }
        runCurrent()

        // The new composition attaches before the old one detaches (#511's interleaving).
        coordinator.attachHost()
        coordinator.detachHost()
        runCurrent()

        assertTrue(ws.shouldReconnectForTest)
    }

    @Test
    fun should_start_the_session_only_after_a_new_user_finishes_onboarding() = runTest {
        val session = testSessionManager(storage)
        val ws = wsClient(backgroundScope)
        val coordinator = coordinator(session, ws).apply { start(); attachHost() }
        coordinator.onRenderedLanguage("tr")
        runCurrent()

        session.signIn("t", "r", "a", "d", isNewUser = true)
        runCurrent()
        assertEquals(ConnectionState.DISCONNECTED, ws.connectionState.value)
        assertEquals(emptyList(), pushRegistrations)

        session.completeProfile()
        runCurrent()

        assertEquals(ConnectionState.WAITING_FOR_AUTH, ws.connectionState.value)
        assertEquals(listOf("tr"), pushRegistrations)
        assertTrue("attach:a" in wiringLog && "schedule" in wiringLog)
    }

    @Test
    fun should_not_restart_anything_while_the_token_is_merely_refreshing() = runTest {
        storage.saveTokens("t", "r", "a", "d")
        val session = testSessionManager(storage)
        val ws = wsClient(backgroundScope)
        val coordinator = coordinator(session, ws).apply { start(); attachHost() }
        coordinator.onRenderedLanguage("en")
        runCurrent()
        wiringLog.clear()

        session.onRefreshStarted()
        runCurrent()
        session.onRefreshSucceeded()
        runCurrent()

        assertEquals(emptyList(), wiringLog)
        assertEquals(listOf("en"), pushRegistrations)
        assertTrue(ws.shouldReconnectForTest)
    }
}
