package com.muhabbet.app.ui.chat

import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.Message
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.Instant

/**
 * A sender's tick only ever moves forward.
 *
 * The recipient now acks DELIVERED only after the message is on disk, so with the chat open its
 * READ routinely reaches the server — and comes back to the sender — first. A late DELIVERED
 * frame must not pull a READ bubble back to grey.
 */
class StatusUpdatesTest {

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun mine(id: String, status: MessageStatus) = Message(
        id = id,
        conversationId = CONVERSATION,
        senderId = ME,
        contentType = ContentType.TEXT,
        content = "merhaba",
        status = status,
        clientTimestamp = now
    )

    private fun update(id: String, status: MessageStatus) = WsMessage.StatusUpdate(
        messageId = id,
        conversationId = CONVERSATION,
        userId = PEER,
        status = status,
        timestamp = 0
    )

    @Test
    fun should_stay_at_read_when_delivered_arrives_after_read() {
        val afterRead = listOf(mine("m1", MessageStatus.SENT)).withStatusUpdate(update("m1", MessageStatus.READ), ME)
        val afterLateDelivered = afterRead.withStatusUpdate(update("m1", MessageStatus.DELIVERED), ME)

        assertEquals(MessageStatus.READ, afterLateDelivered.single().status)
    }

    @Test
    fun should_move_to_delivered_when_the_message_was_only_sent() {
        val result = listOf(mine("m1", MessageStatus.SENT)).withStatusUpdate(update("m1", MessageStatus.DELIVERED), ME)

        assertEquals(MessageStatus.DELIVERED, result.single().status)
    }

    @Test
    fun should_mark_every_earlier_sent_or_delivered_message_read_when_read_arrives() {
        val messages = listOf(
            mine("m1", MessageStatus.SENT),
            mine("m2", MessageStatus.DELIVERED),
            mine("m3", MessageStatus.SENDING)
        )

        val result = messages.withStatusUpdate(update("m2", MessageStatus.READ), ME)

        assertEquals(
            listOf(MessageStatus.READ, MessageStatus.READ, MessageStatus.SENDING),
            result.map { it.status }
        )
    }

    @Test
    fun should_leave_other_messages_alone_when_a_delivered_update_names_one() {
        val messages = listOf(mine("m1", MessageStatus.SENT), mine("m2", MessageStatus.SENT))

        val result = messages.withStatusUpdate(update("m2", MessageStatus.DELIVERED), ME)

        assertEquals(listOf(MessageStatus.SENT, MessageStatus.DELIVERED), result.map { it.status })
    }

    private companion object {
        const val CONVERSATION = "c1"
        const val ME = "me"
        const val PEER = "peer"
    }
}
