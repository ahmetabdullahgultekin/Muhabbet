package com.muhabbet.app.data.remote

import com.muhabbet.app.data.local.FakeTokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #667: the socket that replaces a lost one must be told which chat is on screen.
 *
 * The server drops its `activeConversation` entry when a user's last socket goes away —
 * `WebSocketSessionManager.forget` — because with no socket it genuinely cannot know any more. The
 * client used to send a `ConversationFocus` frame only when the focused conversation *changed*, so
 * a reconnect with the same chat still open left the server believing the user was looking at
 * nothing, and the very next message produced a push for the conversation they were reading. The
 * suppression itself (#618) works; it was being asked a question the server no longer had an answer
 * to.
 *
 * These drive the real connect loop over a [FakeWebSocketSession], so the assertion is about frames
 * that actually reached a socket rather than about a helper being callable.
 *
 * The socket is replaced by `disconnect()` + `dropConnection()` + `connect()` rather than by letting
 * the backoff path run, and deliberately so: that path ends in a REST token-refresh call, which Ktor
 * executes on the engine's own dispatcher and therefore outside the test scheduler, making "has it
 * reconnected yet" a race against wall-clock time. The pair used here is what `SessionLifecycle`
 * performs on every Activity recreation anyway, and it reaches the code under test — a new session
 * established — by exactly the same line.
 */
class WsClientFocusReassertTest {

    private val sessions = mutableListOf<FakeWebSocketSession>()

    private fun wsClient(scope: CoroutineScope): WsClient = WsClient(
        apiClient = ApiClient(
            FakeTokenStorage(),
            MockEngine {
                respond(
                    content = """{"data":null}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        ),
        tokenProvider = { "a-token" },
        scope = scope,
        openSession = { openFakeSession() }
    )

    private fun openFakeSession(): WebSocketSession = FakeWebSocketSession().also { sessions.add(it) }

    /**
     * Ends the live socket and asks the client for a connection again, leaving it on a new session.
     *
     * The [runCurrent] in the middle is load-bearing: [WsClient.startConnectLoop] is single-flighted,
     * so a `connect()` that arrives while the previous loop is still parked in its read adopts that
     * loop instead of opening anything. The old loop has to be given the chance to notice its socket
     * is gone first.
     */
    private fun TestScope.replaceSocket(ws: WsClient, generation: Long): Long {
        ws.disconnect(generation)
        sessions.last().dropConnection()
        runCurrent()
        return ws.connect()
    }

    @Test
    fun should_restate_the_focused_conversation_on_the_socket_that_replaces_a_dropped_one() = runTest {
        val ws = wsClient(backgroundScope)
        val first = ws.connect()
        runCurrent()

        assertEquals(1, sessions.size, "precondition: the client should have opened one session")
        ws.setConversationFocus(CONVERSATION)
        runCurrent()
        assertTrue(
            sessions[0].writtenText().any { it.isFocusOn(CONVERSATION) },
            "precondition: opening a chat still reports focus on the live socket"
        )

        // The socket goes away. Nothing about the screen changes — the same chat is still open, so
        // no focus *change* happens and the old code sent nothing ever again.
        replaceSocket(ws, first)
        runCurrent()

        assertEquals(2, sessions.size, "precondition: the client should be on a new session")
        assertTrue(
            sessions[1].writtenText().any { it.isFocusOn(CONVERSATION) },
            "the new socket must be told which chat is on screen — the server cleared its copy when " +
                "the old one closed, so without this the next message pushes a notification for the " +
                "conversation the user is reading"
        )
    }

    @Test
    fun should_not_restate_a_chat_the_user_has_left() = runTest {
        val ws = wsClient(backgroundScope)
        val first = ws.connect()
        runCurrent()

        ws.setConversationFocus(CONVERSATION)
        runCurrent()
        // Back press: ChatScreen's onDispose clears focus. The client must forget it too, or the
        // re-assert would put the user back in a chat they walked out of — suppressing pushes for
        // a conversation nobody is looking at, which is the same bug pointing the other way.
        ws.setConversationFocus(null)
        runCurrent()

        replaceSocket(ws, first)
        runCurrent()

        assertEquals(2, sessions.size, "precondition: the client should be on a new session")
        assertTrue(
            sessions[1].writtenText().none { it.isFocusOn(CONVERSATION) },
            "a conversation the user has left must not be re-asserted on the new socket"
        )
    }

    private fun String.isFocusOn(conversationId: String): Boolean =
        contains("presence.conversation_focus") && contains(conversationId)

    private companion object {
        const val CONVERSATION = "11111111-2222-3333-4444-555555555555"
    }
}
