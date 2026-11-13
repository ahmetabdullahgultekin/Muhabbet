package com.muhabbet.app.data.remote

import com.muhabbet.app.data.outbox.FakeOutboxStore
import com.muhabbet.app.data.outbox.OutboxPolicy
import com.muhabbet.app.data.outbox.OutboxState
import com.muhabbet.app.data.outbox.SendFailure
import com.muhabbet.app.data.sync.MissedMessageCatchUp
import com.muhabbet.app.data.sync.SyncWatermarkStore
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.Message
import com.muhabbet.shared.protocol.AckStatus
import com.muhabbet.shared.protocol.WsMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The outbox and the gap-fill wired through the real client (#792, #795, #797): what a user
 * actually experiences across a drop, a dead socket and a restart.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WsClientOutboxTest {

    private fun text(id: String) = WsMessage.SendMessage(requestId = "req-$id", messageId = id, conversationId = "c1", content = id)

    @Test
    fun should_accept_a_message_while_offline_and_send_it_once_connected() = runTest {
        val h = WsClientHarness(this)

        h.client.send(text("m1")) // No socket at all: accepted, not thrown.
        assertEquals(OutboxState.Queued, h.store?.rows?.get("m1")?.state)

        h.client.connect()
        runCurrent()
        assertEquals(listOf("m1"), h.sessions.single().sentMessages().map { it.messageId })
    }

    @Test
    fun should_say_so_when_a_message_cannot_be_kept_at_all() = runTest {
        assertFailsWith<MessageCouldNotBeQueuedException> { WsClientHarness(this, store = null).client.send(text("m1")) }
        assertFailsWith<MessageCouldNotBeQueuedException> {
            WsClientHarness(this, store = FakeOutboxStore(failWrites = true)).client.send(text("m2"))
        }
    }

    @Test
    fun should_queue_a_view_once_photo_offline_and_deliver_it_still_sealed() = runTest {
        val h = WsClientHarness(this)
        val photo = WsMessage.SendMessage(
            requestId = "r", messageId = "vo", conversationId = "c1", content = "", contentType = ContentType.IMAGE,
            mediaUrl = "https://cdn/x.jpg", mediaId = "media-1", viewOnce = true
        )

        h.client.send(photo)
        h.client.connect()
        runCurrent()

        assertEquals(photo, h.sessions.single().sentMessages().single(), "it used to be refused, or arrive unsealed (#515, #795)")
    }

    @Test
    fun should_resend_a_message_whose_socket_died_after_the_write() = runTest {
        val h = WsClientHarness(this)
        h.client.connect()
        runCurrent()
        h.client.send(text("m1"))
        runCurrent()
        assertEquals(1, h.sessions[0].sentMessages().size, "written to the first socket, never acknowledged")

        h.sessions[0].dropConnection()
        runCurrent()
        advanceTimeBy(1_500)
        runCurrent()

        assertEquals(listOf("m1"), h.sessions[1].sentMessages().map { it.messageId }, "same messageId, on the new socket")
    }

    @Test
    fun should_confirm_on_the_ack_and_fail_visibly_on_a_refusal() = runTest {
        val h = WsClientHarness(this)
        h.client.connect()
        runCurrent()
        h.client.send(text("ok"))
        h.client.send(text("no"))
        runCurrent()
        val socket = h.sessions.single()

        socket.serverSends(WsMessage.ServerAck("req-ok", "ok", AckStatus.OK, serverTimestamp = 5))
        socket.serverSends(WsMessage.ServerAck("req-no", "no", AckStatus.ERROR, errorCode = "MSG_NOT_MEMBER"))
        runCurrent()

        val states = checkNotNull(h.outbox).messages.value
        assertEquals(OutboxState.Sent(5), states.getValue("ok").state)
        assertEquals(OutboxState.Failed(SendFailure.Rejected("MSG_NOT_MEMBER")), states.getValue("no").state)
        assertEquals(setOf("no"), checkNotNull(h.store).rows.keys, "the acked row is gone; the failed one survives a restart")
    }

    @Test
    fun should_not_let_a_dead_socket_swallow_a_message() = runTest {
        // The socket takes the write and never answers anything: the half-open case end to end.
        val h = WsClientHarness(this, outboxPolicy = OutboxPolicy(baseAckTimeoutMs = 5_000))
        h.client.connect()
        runCurrent()
        h.sessions[0].answerPings = false
        h.client.send(text("m1"))
        runCurrent()

        advanceTimeBy(WsClient.PING_INTERVAL_MS + WsClient.PONG_TIMEOUT_MS + 1_500)
        runCurrent()

        assertTrue(h.sessions.size >= 2)
        assertTrue(h.sessions.last().sentMessages().any { it.messageId == "m1" }, "re-sent on the replacement socket")
    }

    @Test
    fun should_replay_what_arrived_while_the_socket_was_down() = runTest {
        val watermark = object : SyncWatermarkStore {
            var value: Long? = null
            override fun read() = value
            override fun advanceTo(epochMillis: Long) {
                val current = value
                if (current == null || epochMillis > current) value = epochMillis
            }
        }
        val asked = mutableListOf<String>()
        val missed = listOf(
            message("gap-1", serverMs = 2_000),
            message("gap-2", serverMs = 3_000),
            message("live", serverMs = 1_000) // already seen live; not newer than the watermark
        )
        val catchUp = MissedMessageCatchUp(watermark) { since -> asked += since; missed }
        val h = WsClientHarness(this, catchUp = catchUp)
        val seen = mutableListOf<String>()
        backgroundScope.launch { h.client.incoming.collect { if (it is WsMessage.NewMessage) seen += it.messageId } }

        h.client.connect()
        runCurrent()
        h.sessions[0].serverSends(
            WsMessage.NewMessage("live", "c1", "peer", null, "live", ContentType.TEXT, serverTimestamp = 1_000)
        )
        runCurrent()
        assertEquals(1_000L, watermark.value, "server time from the live stream, never the device clock")

        h.sessions[0].dropConnection()
        runCurrent()
        advanceTimeBy(1_500)
        runCurrent()

        assertEquals(listOf(Instant.fromEpochMilliseconds(1_000).toString()), asked.takeLast(1))
        assertEquals(listOf("live", "gap-1", "gap-2"), seen, "each message once, in order, without reopening the chat")
        assertEquals(3_000L, watermark.value)
    }

    private fun message(id: String, serverMs: Long) = Message(
        id = id, conversationId = "c1", senderId = "peer", contentType = ContentType.TEXT, content = id,
        serverTimestamp = Instant.fromEpochMilliseconds(serverMs), clientTimestamp = Instant.fromEpochMilliseconds(serverMs)
    )
}
