package com.muhabbet.app.ui.chat

import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The read-receipt decision an open chat makes, pinned away from the message list.
 *
 * #478: the receipt used to live inside `if (… && messages.none { it.id == ws.messageId })`, so any
 * path that had already put the message on screen consumed the frame and the sender was never told
 * it had been read. The decision now depends on the acked-set and nothing else, which is what these
 * tests hold in place — none of them can express "is it already rendered", because that input no
 * longer exists.
 */
class ReadReceiptsTest {

    private class RecordingSender(private val succeed: Boolean = true) {
        val sent = mutableListOf<WsMessage.AckMessage>()
        suspend fun send(ack: WsMessage.AckMessage): Boolean {
            sent += ack
            return succeed
        }
    }

    @Test
    fun should_publish_a_receipt_when_a_message_arrives_for_the_first_time() = runTest {
        val sender = RecordingSender()
        val acked = AckedMessageIds()

        sendReadReceipt(acked, CONVERSATION, "m1", send = sender::send)

        assertEquals(1, sender.sent.size, "The first sight of a message must produce a receipt.")
        assertEquals("m1", sender.sent.single().messageId)
        assertEquals(CONVERSATION, sender.sent.single().conversationId)
        assertEquals(
            MessageStatus.READ,
            sender.sent.single().status,
            "An open chat publishes READ; DELIVERED is the app-wide pump's job."
        )
    }

    @Test
    fun should_still_publish_a_receipt_when_the_message_was_already_on_screen() = runTest {
        // The regression itself. A refresh, the SQLDelight cache or a pagination fetch can put the
        // message into the list before the WebSocket frame is handled. sendReadReceipt is not given
        // the list at all, so there is no way for that to suppress the receipt — the only thing that
        // can is a receipt already sent for this id, which is a different fact entirely.
        val sender = RecordingSender()
        val acked = AckedMessageIds()

        sendReadReceipt(acked, CONVERSATION, "already-rendered", send = sender::send)

        assertEquals(1, sender.sent.size)
    }

    @Test
    fun should_stay_quiet_when_the_same_message_is_seen_again() = runTest {
        val sender = RecordingSender()
        val acked = AckedMessageIds()

        repeat(5) { sendReadReceipt(acked, CONVERSATION, "m1", send = sender::send) }

        assertEquals(
            1,
            sender.sent.size,
            "Moving the receipt out of the dedup guard must not turn it into one receipt per frame."
        )
    }

    @Test
    fun should_re_assert_the_receipt_when_forced_after_returning_to_the_foreground() = runTest {
        val sender = RecordingSender()
        val acked = AckedMessageIds()

        sendReadReceipt(acked, CONVERSATION, "m1", send = sender::send)
        sendReadReceipt(acked, CONVERSATION, "m1", force = true, send = sender::send)

        assertEquals(
            2,
            sender.sent.size,
            "Coming back to the front re-asserts on purpose: the first receipt may have gone into a " +
                "socket with no peer, and nothing else will ever try again."
        )
    }

    @Test
    fun should_not_treat_a_queued_receipt_as_a_reason_to_stop() = runTest {
        // A `false` from the sender means "queued for the next reconnect", not "dropped". The caller
        // must carry on handling frames either way.
        val sender = RecordingSender(succeed = false)
        val acked = AckedMessageIds()

        sendReadReceipt(acked, CONVERSATION, "m1", send = sender::send)
        sendReadReceipt(acked, CONVERSATION, "m2", send = sender::send)

        assertEquals(listOf("m1", "m2"), sender.sent.map { it.messageId })
    }

    @Test
    fun should_forget_the_oldest_ids_when_the_set_is_full() = runTest {
        val acked = AckedMessageIds(maxSize = 3)

        listOf("a", "b", "c", "d").forEach { acked.markAcked(it) }

        assertEquals(3, acked.size)
        assertTrue("a" !in acked, "Oldest out first, so a long chat cannot grow the set forever.")
        assertTrue("d" in acked)
        assertTrue(
            acked.markAcked("a"),
            "An evicted id reads as new again, which re-sends one harmless idempotent receipt."
        )
    }

    private companion object {
        const val CONVERSATION = "conv-1"
    }
}
