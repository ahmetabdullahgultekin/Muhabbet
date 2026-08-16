package com.muhabbet.app.data.remote

import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The queue that stops a delivery receipt from being lost when the socket is down (#478).
 *
 * Before it, `send()` threw and the call site logged "best-effort; it is re-sent on the next
 * incoming message" — true only if another message ever arrived. In a quiet conversation that meant
 * never, and the sender's ticks stayed wrong until one side restarted the app.
 */
class PendingAckQueueTest {

    private fun ack(messageId: String, status: MessageStatus) =
        WsMessage.AckMessage(messageId = messageId, conversationId = CONVERSATION, status = status)

    @Test
    fun should_replay_receipts_in_the_order_they_were_produced() = runTest {
        val queue = PendingAckQueue()

        queue.record(ack("m1", MessageStatus.DELIVERED))
        queue.record(ack("m2", MessageStatus.DELIVERED))
        queue.record(ack("m3", MessageStatus.READ))

        assertEquals(listOf("m1", "m2", "m3"), queue.takeAll().map { it.messageId })
    }

    @Test
    fun should_empty_itself_when_drained() = runTest {
        val queue = PendingAckQueue()
        queue.record(ack("m1", MessageStatus.READ))

        assertEquals(1, queue.takeAll().size)
        assertEquals(
            emptyList(),
            queue.takeAll(),
            "A drained receipt must not be replayed again on the connect after next."
        )
    }

    @Test
    fun should_upgrade_a_queued_receipt_when_the_message_is_then_read() = runTest {
        val queue = PendingAckQueue()

        queue.record(ack("m1", MessageStatus.DELIVERED))
        queue.record(ack("m1", MessageStatus.READ))

        val drained = queue.takeAll()
        assertEquals(1, drained.size, "One receipt per message, not one per attempt.")
        assertEquals(MessageStatus.READ, drained.single().status)
    }

    @Test
    fun should_not_let_a_late_delivered_undo_a_queued_read() = runTest {
        // Both screens ack: ChatScreen publishes READ, the app-wide pump publishes DELIVERED. Queued
        // in that order and replayed verbatim, the DELIVERED would land last and the sender would
        // watch the message go from read back to delivered.
        val queue = PendingAckQueue()

        queue.record(ack("m1", MessageStatus.READ))
        queue.record(ack("m1", MessageStatus.DELIVERED))

        assertEquals(MessageStatus.READ, queue.takeAll().single().status)
    }

    @Test
    fun should_drop_the_oldest_receipts_when_a_long_outage_fills_it() = runTest {
        val queue = PendingAckQueue(maxSize = 3)

        listOf("m1", "m2", "m3", "m4").forEach { queue.record(ack(it, MessageStatus.DELIVERED)) }

        val drained = queue.takeAll()
        assertEquals(3, drained.size)
        assertEquals(listOf("m2", "m3", "m4"), drained.map { it.messageId })
        assertTrue(
            drained.none { it.messageId == "m1" },
            "An unbounded queue would be a memory leak for an app left offline; the oldest receipts " +
                "are also the ones the next chat open re-asserts anyway."
        )
    }

    @Test
    fun should_report_what_it_is_holding() = runTest {
        val queue = PendingAckQueue()
        assertEquals(0, queue.size())

        queue.record(ack("m1", MessageStatus.READ))
        queue.record(ack("m1", MessageStatus.READ))

        assertEquals(1, queue.size())
    }

    private companion object {
        const val CONVERSATION = "conv-1"
    }
}
