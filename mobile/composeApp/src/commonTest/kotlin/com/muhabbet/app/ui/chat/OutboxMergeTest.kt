package com.muhabbet.app.ui.chat

import com.muhabbet.app.data.outbox.OutboxState
import com.muhabbet.app.data.outbox.OutgoingMessage
import com.muhabbet.app.data.outbox.SendFailure
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.Message
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** How the chat lays the outbox over its list (#792): the reader the old offline queue never had. */
class OutboxMergeTest {

    private val me = "me"

    private fun bubble(id: String, status: MessageStatus) = Message(
        id = id, conversationId = "c1", senderId = me, contentType = ContentType.TEXT, content = id,
        status = status, clientTimestamp = Instant.fromEpochMilliseconds(0)
    )

    private fun outgoing(id: String, state: OutboxState, scheduledAt: Long? = null) = OutgoingMessage(
        frame = WsMessage.SendMessage(
            requestId = "r-$id", messageId = id, conversationId = "c1", content = "body $id",
            replyToId = "q", forwardedFrom = "f", viewOnce = true, scheduledAt = scheduledAt
        ),
        state = state,
        createdAtMs = 1_000
    )

    @Test
    fun should_turn_a_bubble_failed_instead_of_deleting_it() {
        val merged = listOf(bubble("m1", MessageStatus.SENDING))
            .withOutbox(listOf(outgoing("m1", OutboxState.Failed(SendFailure.Exhausted))), me)

        assertEquals(listOf(MessageStatus.FAILED), merged.map { it.status })
    }

    @Test
    fun should_bring_a_failed_bubble_back_to_sending_when_it_is_retried() {
        val merged = listOf(bubble("m1", MessageStatus.FAILED)).withOutbox(listOf(outgoing("m1", OutboxState.Queued)), me)

        assertEquals(MessageStatus.SENDING, merged.single().status)
    }

    @Test
    fun should_not_let_a_stale_outbox_state_regress_a_bubble() {
        val merged = listOf(bubble("m1", MessageStatus.READ)).withOutbox(listOf(outgoing("m1", OutboxState.Sent(1))), me)

        assertEquals(MessageStatus.READ, merged.single().status)
    }

    @Test
    fun should_restore_queued_and_failed_messages_the_list_does_not_hold() {
        val merged = emptyList<Message>().withOutbox(
            listOf(
                outgoing("queued", OutboxState.Queued),
                outgoing("failed", OutboxState.Failed(SendFailure.Rejected("MSG_NOT_MEMBER"))),
                outgoing("sent", OutboxState.Sent(1)),
                outgoing("later", OutboxState.Queued, scheduledAt = 9_999_999)
            ),
            me
        )

        assertEquals(listOf("queued", "failed"), merged.map { it.id }, "confirmed come from history; scheduled live in their chip")
        val restored = merged.first()
        assertEquals(me, restored.senderId)
        assertEquals("body queued", restored.content)
        assertEquals("f", restored.forwardedFrom)
        assertEquals(true, restored.viewOnce)
        assertEquals(MessageStatus.FAILED, merged[1].status)
    }

    @Test
    fun should_leave_the_list_alone_when_the_outbox_is_empty() {
        val list = listOf(bubble("m1", MessageStatus.SENT))
        assertEquals(list, list.withOutbox(emptyList(), me))
    }
}
