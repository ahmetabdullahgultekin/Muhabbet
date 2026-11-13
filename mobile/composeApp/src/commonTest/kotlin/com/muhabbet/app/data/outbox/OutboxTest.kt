package com.muhabbet.app.data.outbox

import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.AckStatus
import com.muhabbet.shared.protocol.WsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The outbox processor (#792, #795) against a fake store and a fake wire, on virtual time.
 *
 * Each acceptance criterion of #792 is one test here: disconnect mid-drain, a write with no ack,
 * an ack, a rate-limit refusal, exhaustion, and a process death.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OutboxTest {

    private val policy = OutboxPolicy(maxAttempts = 3, baseAckTimeoutMs = 1_000, maxAckTimeoutMs = 4_000)

    /** A wire that records frames; it can be told to fail the next write. */
    private class FakeWire : Outbox.Transport {
        val written = mutableListOf<WsMessage.SendMessage>()
        var failNext = false
        override suspend fun write(frame: WsMessage.SendMessage) {
            if (failNext) {
                failNext = false
                throw IllegalStateException("socket closed under the write")
            }
            written += frame
        }
    }

    private fun TestScope.outbox(store: OutboxStore? = FakeOutboxStore(), scope: CoroutineScope = backgroundScope) =
        Outbox(store, scope, clock = { testScheduler.currentTime }, policy = policy)

    private fun frame(id: String, conversation: String = "c1") = WsMessage.SendMessage(
        requestId = "req-$id", messageId = id, conversationId = conversation, content = "body $id"
    )

    private fun ok(id: String, ts: Long = 99) =
        WsMessage.ServerAck(requestId = "req-$id", messageId = id, status = AckStatus.OK, serverTimestamp = ts)

    private fun refused(id: String, code: String) =
        WsMessage.ServerAck(requestId = "req-$id", messageId = id, status = AckStatus.ERROR, errorCode = code)

    private fun Outbox.stateOf(id: String) = messages.value[id]?.state

    @Test
    fun should_persist_before_anything_touches_the_wire() = runTest {
        val store = FakeOutboxStore()
        val box = outbox(store)

        assertTrue(box.enqueue(frame("m1")))

        assertEquals(OutboxState.Queued, store.rows["m1"]?.state, "offline: the row is what keeps the message")
        assertEquals(OutboxState.Queued, box.stateOf("m1"))
    }

    @Test
    fun should_refuse_a_message_it_could_not_persist() = runTest {
        assertFalse(outbox(FakeOutboxStore(failWrites = true)).enqueue(frame("m1")))
        assertFalse(outbox(store = null).enqueue(frame("m2")), "no store means nothing would send it later")
    }

    @Test
    fun should_remove_the_row_only_when_the_server_acks() = runTest {
        val store = FakeOutboxStore()
        val box = outbox(store)
        val wire = FakeWire()
        box.attach(wire)

        box.enqueue(frame("m1"))
        runCurrent()
        assertEquals(listOf("m1"), wire.written.map { it.messageId })
        assertIs<OutboxState.Sending>(store.rows["m1"]?.state, "written is not delivered: the row stays")

        box.onServerAck(ok("m1"))

        assertFalse("m1" in store.rows)
        assertEquals(OutboxState.Sent(99), box.stateOf("m1"))
    }

    @Test
    fun should_keep_the_row_when_the_socket_dies_during_the_drain() = runTest {
        val store = FakeOutboxStore()
        val box = outbox(store)
        box.enqueue(frame("m1"))
        box.enqueue(frame("m2"))

        val wire = FakeWire().apply { failNext = true }
        box.attach(wire)
        box.drain()

        assertEquals(OutboxState.Queued, store.rows["m1"]?.state)
        assertEquals(OutboxState.Queued, store.rows["m2"]?.state)
        assertEquals(0, store.rows.getValue("m1").attempts, "a dead socket is not an unanswered attempt")
    }

    @Test
    fun should_send_again_with_the_same_message_id_when_no_ack_arrives() = runTest {
        val box = outbox()
        val wire = FakeWire()
        box.attach(wire)
        box.enqueue(frame("m1"))
        runCurrent()

        advanceTimeBy(1_001)
        runCurrent()

        assertEquals(listOf("m1", "m1"), wire.written.map { it.messageId })
        assertEquals(wire.written[0], wire.written[1], "a retry is the identical frame, so the server can de-duplicate it")
        assertEquals(OutboxState.Sending(attempt = 2, deadlineMs = 1_000 + 2_000), box.stateOf("m1"))
    }

    @Test
    fun should_fail_visibly_after_the_last_attempt_and_never_delete() = runTest {
        val store = FakeOutboxStore()
        val box = outbox(store)
        box.attach(FakeWire())
        box.enqueue(frame("m1"))
        runCurrent()

        advanceTimeBy(1_000 + 2_000 + 4_000 + 10)
        runCurrent()

        assertEquals(OutboxState.Failed(SendFailure.Exhausted), box.stateOf("m1"))
        assertEquals(OutboxState.Failed(SendFailure.Exhausted), store.rows["m1"]?.state, "Failed must survive a restart")
        val seen = box.forConversation("c1").first().single()
        assertEquals(MessageStatus.FAILED, seen.state.toMessageStatus())
    }

    @Test
    fun should_requeue_a_rate_limited_message_and_send_it_after_waiting() = runTest {
        val box = outbox()
        val wire = FakeWire()
        box.attach(wire)
        box.enqueue(frame("m1"))
        runCurrent()

        box.onServerAck(refused("m1", "RATE_LIMITED"))
        assertEquals(OutboxState.Queued, box.stateOf("m1"))
        assertEquals(1, wire.written.size, "not hammered straight back at the limiter")

        advanceTimeBy(2_001)
        runCurrent()
        assertEquals(2, wire.written.size)
    }

    @Test
    fun should_fail_with_the_code_on_a_permanent_refusal() = runTest {
        val box = outbox()
        box.attach(FakeWire())
        box.enqueue(frame("m1"))
        runCurrent()

        box.onServerAck(refused("m1", "MSG_ANNOUNCEMENT_ONLY"))

        assertEquals(OutboxState.Failed(SendFailure.Rejected("MSG_ANNOUNCEMENT_ONLY")), box.stateOf("m1"))
    }

    @Test
    fun should_send_a_failed_message_again_when_the_user_retries() = runTest {
        val box = outbox()
        val wire = FakeWire()
        box.attach(wire)
        box.enqueue(frame("m1"))
        runCurrent()
        box.onServerAck(refused("m1", "MSG_ANNOUNCEMENT_ONLY"))

        box.retry("m1")
        runCurrent()

        assertEquals(2, wire.written.size)
        assertIs<OutboxState.Sending>(box.stateOf("m1"))
    }

    @Test
    fun should_requeue_in_flight_messages_without_cost_when_the_connection_goes() = runTest {
        val box = outbox()
        box.attach(FakeWire())
        box.enqueue(frame("m1"))
        runCurrent()

        box.detach()

        assertEquals(OutboxState.Queued, box.stateOf("m1"))
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(OutboxState.Queued, box.stateOf("m1"), "no deadline runs against a message with no connection")
    }

    @Test
    fun should_resend_queued_and_in_flight_messages_after_a_process_death() = runTest {
        val store = FakeOutboxStore()
        val first = outbox(store)
        first.enqueue(frame("queued"))
        first.attach(FakeWire())
        first.enqueue(frame("inflight"))
        runCurrent()
        assertIs<OutboxState.Sending>(store.rows["inflight"]?.state)

        // A new process: same table, new outbox.
        val second = outbox(store)
        val wire = FakeWire()
        second.attach(wire)
        second.drain()
        runCurrent()

        assertEquals(setOf("queued", "inflight"), wire.written.map { it.messageId }.toSet())
    }

    @Test
    fun should_restore_a_failed_message_after_a_restart_still_failed() = runTest {
        val store = FakeOutboxStore()
        store.save(OutgoingMessage(frame("m1"), OutboxState.Failed(SendFailure.Exhausted), attempts = 3, createdAtMs = 1))

        val box = outbox(store)
        runCurrent()

        assertEquals(OutboxState.Failed(SendFailure.Exhausted), box.stateOf("m1"))
    }

    @Test
    fun should_carry_every_frame_field_through_the_queue_unchanged() = runTest {
        val full = WsMessage.SendMessage(
            requestId = "r", messageId = "m", conversationId = "c", content = "Photo",
            contentType = com.muhabbet.shared.model.ContentType.IMAGE, replyToId = "reply",
            mediaUrl = "https://cdn/x.jpg", thumbnailUrl = "https://cdn/x_t.jpg", mediaId = "media-1",
            forwardedFrom = "orig", viewOnce = true, scheduledAt = 1_800_000_000_000
        )
        val box = outbox()
        box.enqueue(full)
        val wire = FakeWire()
        box.attach(wire)
        box.drain()

        assertEquals(full, wire.written.single(), "scheduledAt, viewOnce, mediaId, thumbnailUrl, forwardedFrom all intact (#795)")
    }

    @Test
    fun should_apply_receipts_forward_only() = runTest {
        val box = outbox()
        box.attach(FakeWire())
        box.enqueue(frame("m1"))
        runCurrent()
        box.onServerAck(ok("m1"))

        box.onStatusUpdate(WsMessage.StatusUpdate("m1", "c1", "peer", MessageStatus.READ, 1))
        box.onStatusUpdate(WsMessage.StatusUpdate("m1", "c1", "peer", MessageStatus.DELIVERED, 2))

        assertEquals(OutboxState.Read, box.stateOf("m1"))
    }

    @Test
    fun should_expose_only_the_conversation_asked_for() = runTest {
        val box = outbox()
        box.enqueue(frame("a", conversation = "c1"))
        box.enqueue(frame("b", conversation = "c2"))

        assertEquals(listOf("a"), box.forConversation("c1").first().map { it.messageId })
    }
}
