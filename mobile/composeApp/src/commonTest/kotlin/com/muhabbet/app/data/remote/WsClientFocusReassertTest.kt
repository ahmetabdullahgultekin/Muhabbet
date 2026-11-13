package com.muhabbet.app.data.remote

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #667: the socket that replaces a lost one must be told which chat is on screen.
 *
 * The server drops its `activeConversation` entry when a user's last socket goes away, so a
 * reconnect with the same chat still open left it believing the user was looking at nothing, and the
 * next message pushed a notification for the conversation being read.
 *
 * The socket is now replaced the way a network drop replaces it — the old one closes, the client
 * backs off and opens a new one. That path used to end in a REST call executed outside the test
 * scheduler, which is why these tests once simulated it with disconnect()+connect(); the client no
 * longer makes that call (#811), so the real path is deterministic on virtual time.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WsClientFocusReassertTest {

    private fun TestScope.dropAndReconnect(h: WsClientHarness) {
        h.sessions.last().dropConnection()
        runCurrent()
        advanceTimeBy(1_500)
        runCurrent()
    }

    @Test
    fun should_restate_the_focused_conversation_on_the_socket_that_replaces_a_dropped_one() = runTest {
        val h = WsClientHarness(this)
        h.client.connect()
        runCurrent()

        h.client.setConversationFocus(CONVERSATION)
        runCurrent()
        assertTrue(h.sessions[0].writtenText().any { it.isFocusOn(CONVERSATION) }, "precondition: focus reported live")

        dropAndReconnect(h)

        assertEquals(2, h.sessions.size, "precondition: the client should be on a new session")
        assertTrue(
            h.sessions[1].writtenText().any { it.isFocusOn(CONVERSATION) },
            "the new socket must be told which chat is on screen"
        )
    }

    @Test
    fun should_not_restate_a_chat_the_user_has_left() = runTest {
        val h = WsClientHarness(this)
        h.client.connect()
        runCurrent()

        h.client.setConversationFocus(CONVERSATION)
        h.client.setConversationFocus(null)
        runCurrent()

        dropAndReconnect(h)

        assertEquals(2, h.sessions.size, "precondition: the client should be on a new session")
        assertTrue(h.sessions[1].writtenText().none { it.isFocusOn(CONVERSATION) })
    }

    private fun String.isFocusOn(conversationId: String): Boolean =
        contains("presence.conversation_focus") && contains(conversationId)

    private companion object {
        const val CONVERSATION = "11111111-2222-3333-4444-555555555555"
    }
}
