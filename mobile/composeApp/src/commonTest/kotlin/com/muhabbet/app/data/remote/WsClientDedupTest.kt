package com.muhabbet.app.data.remote

import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.MessageStatus
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #726 — a frame is deduplicated by the event it describes, not by the message it is about.
 *
 * `WsClient` keeps one set of keys for every frame type it receives. `MessageDeleted` keyed on the
 * bare `messageId`, which is the key the `NewMessage` that delivered that message had already put
 * in the set, so a "delete for everyone" arriving over the socket that carried the original was
 * discarded as a duplicate and the message stayed on screen. Reconnecting emptied the set, which is
 * why it looked intermittent.
 *
 * These drive the real connect loop over a [FakeWebSocketSession] and assert on what comes out of
 * `incoming`, so they exercise decode → dedup → emit rather than calling a key function directly.
 * A test that only compared two keys would pass against a private helper that the receive path had
 * stopped using.
 */
class WsClientDedupTest {

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
        openSession = { FakeWebSocketSession().also { sessions.add(it) } }
    )

    /**
     * Brings a client up with a collector already attached and feeds it [frames] over one socket.
     *
     * The collector is started first on purpose: `incoming` is a `MutableSharedFlow` with no replay,
     * so anything emitted before somebody is listening is gone.
     */
    private fun TestScope.receive(vararg frames: WsMessage): List<WsMessage> {
        val ws = wsClient(backgroundScope)
        val received = mutableListOf<WsMessage>()
        backgroundScope.launch { ws.incoming.collect { received += it } }
        runCurrent()

        ws.connect()
        runCurrent()

        val socket = sessions.last()
        frames.forEach { socket.deliverText(wsJson.encodeToString<WsMessage>(it)) }
        runCurrent()

        return received
    }

    private fun newMessage(id: String) = WsMessage.NewMessage(
        messageId = id,
        conversationId = CONVERSATION,
        senderId = SENDER,
        senderName = "Ada",
        content = "merhaba",
        contentType = ContentType.TEXT,
        serverTimestamp = 1_700_000_000_000
    )

    private fun deleted(id: String) = WsMessage.MessageDeleted(
        messageId = id,
        conversationId = CONVERSATION,
        deletedBy = SENDER,
        timestamp = 1_700_000_001_000
    )

    private fun edited(id: String, at: Long) = WsMessage.MessageEdited(
        messageId = id,
        conversationId = CONVERSATION,
        editedBy = SENDER,
        newContent = "düzeltildi $at",
        editedAt = at
    )

    /**
     * The bug itself. Both frames arrive over one socket, so both are measured against the same
     * dedup set — the situation a recipient who has not reconnected is always in.
     */
    @Test
    fun should_deliver_a_deletion_that_arrives_over_the_socket_that_carried_the_message() = runTest {
        val received = receive(newMessage(MESSAGE_ID), deleted(MESSAGE_ID))

        assertTrue(
            received.any { it is WsMessage.MessageDeleted },
            "the deletion was swallowed as a duplicate of the message it deletes: $received"
        )
        assertEquals(2, received.size, "expected the message and its deletion, got $received")
    }

    /** The dedup set still has to do its job: a frame repeated verbatim is dropped. */
    @Test
    fun should_still_drop_a_deletion_that_arrives_twice() = runTest {
        val received = receive(deleted(MESSAGE_ID), deleted(MESSAGE_ID))

        assertEquals(1, received.size, "expected one deletion, got $received")
    }

    /**
     * The same collision one frame type over: a status change is an event about a message the client
     * has already seen, and `_expired` / `_edited` were each added because their author hit this.
     */
    @Test
    fun should_deliver_every_frame_type_that_can_follow_a_message_over_one_socket() = runTest {
        val received = receive(
            newMessage(MESSAGE_ID),
            WsMessage.StatusUpdate(
                messageId = MESSAGE_ID,
                conversationId = CONVERSATION,
                userId = SENDER,
                status = MessageStatus.DELIVERED,
                timestamp = 1_700_000_002_000
            ),
            edited(MESSAGE_ID, at = 1_700_000_003_000),
            WsMessage.MessageExpired(
                messageId = MESSAGE_ID,
                conversationId = CONVERSATION,
                expiredAt = 1_700_000_004_000
            ),
            deleted(MESSAGE_ID)
        )

        assertEquals(5, received.size, "a frame about an already-seen message was dropped: $received")
    }

    /**
     * A key must separate two real events of the same kind, not just two kinds of event. Under the
     * old `<id>_edited` key every edit of a message after the first was discarded, so a correction
     * to a correction never appeared.
     */
    @Test
    fun should_deliver_a_second_edit_of_the_same_message() = runTest {
        val received = receive(
            edited(MESSAGE_ID, at = 1_700_000_003_000),
            edited(MESSAGE_ID, at = 1_700_000_009_000)
        )

        assertEquals(2, received.size, "the second edit was dropped as a repeat of the first: $received")
    }

    private companion object {
        const val CONVERSATION = "3f1b2c8e-0000-4000-8000-000000000001"
        const val SENDER = "3f1b2c8e-0000-4000-8000-000000000002"
        const val MESSAGE_ID = "3f1b2c8e-0000-4000-8000-000000000003"
    }
}
