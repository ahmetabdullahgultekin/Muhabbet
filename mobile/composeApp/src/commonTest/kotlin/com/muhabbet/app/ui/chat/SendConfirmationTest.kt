package com.muhabbet.app.ui.chat

import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.Message
import com.muhabbet.shared.model.MessageStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.Instant

/**
 * #836: `MessageSent` fires when the server confirms a send, exactly once — never on the tap, and
 * never again for the same message.
 */
class SendConfirmationTest {

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun message(id: String, status: MessageStatus) = Message(
        id = id,
        conversationId = "conv-1",
        senderId = "me",
        contentType = ContentType.TEXT,
        content = "merhaba",
        status = status,
        clientTimestamp = now
    )

    @Test
    fun `should confirm when the ack is for a message still sending`() {
        assertTrue(ackConfirmsPendingSend(listOf(message("m1", MessageStatus.SENDING)), "m1"))
    }

    @Test
    fun `should stay silent when the same message is acked a second time`() {
        // The screen applies the first ack by moving the message to SENT; the duplicate that follows
        // an offline-queue drain then finds nothing pending.
        val afterFirstAck = listOf(message("m1", MessageStatus.SENT))
        assertFalse(ackConfirmsPendingSend(afterFirstAck, "m1"))
    }

    @Test
    fun `should stay silent when the acked message is not on this screen`() {
        assertFalse(ackConfirmsPendingSend(listOf(message("m1", MessageStatus.SENDING)), "other"))
    }

    @Test
    fun `should stay silent when the message has already progressed past sent`() {
        listOf(MessageStatus.DELIVERED, MessageStatus.READ).forEach { status ->
            assertFalse(ackConfirmsPendingSend(listOf(message("m1", status)), "m1"))
        }
    }
}
