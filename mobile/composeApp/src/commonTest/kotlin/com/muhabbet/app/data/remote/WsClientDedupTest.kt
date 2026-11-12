package com.muhabbet.app.data.remote

import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the client's duplicate filter lets through (MOB-05, and the collision noted in 9e26356).
 *
 * The filter is one set shared by every frame type, keyed per frame. Two keys were wrong:
 * - an edit was keyed on the message alone, so the second edit of a message — a different text with
 *   a later `editedAt` — was dropped as a duplicate of the first and the chat kept showing the old one;
 * - a deletion was keyed on the bare message id, which is exactly the key the `message.new` that
 *   delivered the message had already claimed, so a delete arriving on the same socket was dropped
 *   and the bubble never turned into a tombstone.
 *
 * Driven through the real connect loop over a [FakeWebSocketSession], so the assertions are about
 * what actually reaches [WsClient.incoming].
 */
class WsClientDedupTest {

    private val sessions = mutableListOf<FakeWebSocketSession>()

    private fun wsClient(scope: CoroutineScope): WsClient = WsClient(
        apiClient = ApiClient(FakeTokenStorage(), MockEngine { respond("", HttpStatusCode.OK) }),
        tokenProvider = { "a-token" },
        scope = scope,
        openSession = { FakeWebSocketSession().also { sessions.add(it) } }
    )

    private fun frame(message: WsMessage): String = wsJson.encodeToString(WsMessage.serializer(), message)

    @Test
    fun `should deliver a second edit when it carries a later editedAt`() = runTest {
        val ws = wsClient(backgroundScope)
        val seen = mutableListOf<WsMessage>()
        backgroundScope.launch { ws.incoming.collect { seen += it } }
        ws.connect()
        runCurrent()

        sessions.single().deliver(frame(edit(content = "first", editedAt = 1_000L)))
        sessions.single().deliver(frame(edit(content = "second", editedAt = 2_000L)))
        runCurrent()

        assertEquals(
            listOf("first", "second"),
            seen.filterIsInstance<WsMessage.MessageEdited>().map { it.newContent },
            "every edit of a message is a new fact; dropping the second left the first text on screen"
        )
    }

    @Test
    fun `should still drop a replay of the same edit`() = runTest {
        val ws = wsClient(backgroundScope)
        val seen = mutableListOf<WsMessage>()
        backgroundScope.launch { ws.incoming.collect { seen += it } }
        ws.connect()
        runCurrent()

        sessions.single().deliver(frame(edit(content = "first", editedAt = 1_000L)))
        sessions.single().deliver(frame(edit(content = "first", editedAt = 1_000L)))
        runCurrent()

        assertEquals(1, seen.filterIsInstance<WsMessage.MessageEdited>().size)
    }

    @Test
    fun `should deliver a deletion of a message that arrived on the same socket`() = runTest {
        val ws = wsClient(backgroundScope)
        val seen = mutableListOf<WsMessage>()
        backgroundScope.launch { ws.incoming.collect { seen += it } }
        ws.connect()
        runCurrent()

        sessions.single().deliver(frame(newMessage()))
        sessions.single().deliver(
            frame(WsMessage.MessageDeleted(MESSAGE, CONVERSATION, deletedBy = "user-b", timestamp = 3_000L))
        )
        runCurrent()

        assertEquals(1, seen.filterIsInstance<WsMessage.NewMessage>().size)
        assertEquals(
            1,
            seen.filterIsInstance<WsMessage.MessageDeleted>().size,
            "the deletion shares the bare message id with the message.new that delivered it"
        )
    }

    private fun edit(content: String, editedAt: Long) =
        WsMessage.MessageEdited(MESSAGE, CONVERSATION, editedBy = "user-b", newContent = content, editedAt = editedAt)

    private fun newMessage() = WsMessage.NewMessage(
        messageId = MESSAGE,
        conversationId = CONVERSATION,
        senderId = "user-b",
        senderName = null,
        content = "hello",
        contentType = ContentType.TEXT,
        serverTimestamp = 500L
    )

    private companion object {
        const val MESSAGE = "msg-1"
        const val CONVERSATION = "conv-1"
    }
}
