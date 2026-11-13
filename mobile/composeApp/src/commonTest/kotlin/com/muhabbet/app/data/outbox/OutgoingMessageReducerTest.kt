package com.muhabbet.app.data.outbox

import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

/** The pure outbox transition function (#792, #879). No coroutines, no I/O. */
class OutgoingMessageReducerTest {

    private val policy = OutboxPolicy(maxAttempts = 3, baseAckTimeoutMs = 1_000, maxAckTimeoutMs = 4_000)

    private fun message(state: OutboxState = OutboxState.Queued, attempts: Int = 0) = OutgoingMessage(
        frame = WsMessage.SendMessage(requestId = "r", messageId = "m", conversationId = "c", content = "x"),
        state = state,
        attempts = attempts,
        createdAtMs = 0
    )

    private val sending = OutboxState.Sending(attempt = 1, deadlineMs = 1_000)

    @Test
    fun should_put_a_queued_message_in_flight_with_a_deadline_when_dispatched() {
        val next = message().reduce(OutboxEvent.Dispatched(nowMs = 500, ackTimeoutMs = 1_000), policy)
        assertEquals(OutboxState.Sending(attempt = 1, deadlineMs = 1_500), next.state)
        assertEquals(0, next.attempts, "a dispatch is not an unanswered attempt until its deadline passes")
    }

    @Test
    fun should_confirm_on_an_ok_ack() {
        assertEquals(OutboxState.Sent(42), message(sending).reduce(OutboxEvent.Acked(42), policy).state)
    }

    @Test
    fun should_treat_a_duplicate_refusal_as_proof_that_an_earlier_copy_landed() {
        val next = message(sending).reduce(OutboxEvent.Rejected(OutboxPolicy.CODE_DUPLICATE), policy)
        assertIs<OutboxState.Sent>(next.state)
    }

    @Test
    fun should_fail_with_the_code_on_a_permanent_refusal() {
        val next = message(sending).reduce(OutboxEvent.Rejected("MSG_NOT_MEMBER"), policy)
        assertEquals(OutboxState.Failed(SendFailure.Rejected("MSG_NOT_MEMBER")), next.state)
    }

    @Test
    fun should_requeue_and_spend_an_attempt_when_rate_limited() {
        val next = message(sending).reduce(OutboxEvent.Rejected("RATE_LIMITED"), policy)
        assertEquals(OutboxState.Queued, next.state)
        assertEquals(1, next.attempts)
    }

    @Test
    fun should_requeue_when_the_ack_deadline_passes() {
        val next = message(sending).reduce(OutboxEvent.AckTimedOut, policy)
        assertEquals(OutboxState.Queued, next.state)
        assertEquals(1, next.attempts)
    }

    @Test
    fun should_fail_as_exhausted_after_the_last_unanswered_attempt() {
        val next = message(sending, attempts = 2).reduce(OutboxEvent.AckTimedOut, policy)
        assertEquals(OutboxState.Failed(SendFailure.Exhausted), next.state)
        assertEquals(3, next.attempts)
    }

    @Test
    fun should_not_spend_an_attempt_when_the_connection_drops_mid_flight() {
        val next = message(sending, attempts = 2).reduce(OutboxEvent.ConnectionLost, policy)
        assertEquals(OutboxState.Queued, next.state)
        assertEquals(2, next.attempts, "flaky reconnects alone must never exhaust a message")
    }

    @Test
    fun should_leave_failed_only_on_an_explicit_retry_with_a_fresh_budget() {
        val failed = message(OutboxState.Failed(SendFailure.Exhausted), attempts = 3)
        for (event in listOf(OutboxEvent.AckTimedOut, OutboxEvent.ConnectionLost, OutboxEvent.Dispatched(0, 1))) {
            assertSame(failed, failed.reduce(event, policy), "$event must not move a failed message")
        }
        val retried = failed.reduce(OutboxEvent.RetryRequested, policy)
        assertEquals(OutboxState.Queued, retried.state)
        assertEquals(0, retried.attempts)
    }

    @Test
    fun should_accept_a_late_ack_for_a_message_it_had_given_up_on() {
        val failed = message(OutboxState.Failed(SendFailure.Exhausted))
        assertEquals(OutboxState.Sent(7), failed.reduce(OutboxEvent.Acked(7), policy).state)
    }

    @Test
    fun should_never_move_a_confirmed_message_backwards() {
        val read = message(OutboxState.Read)
        val events = listOf(
            OutboxEvent.Acked(1), OutboxEvent.Rejected("MSG_NOT_MEMBER"), OutboxEvent.AckTimedOut,
            OutboxEvent.ConnectionLost, OutboxEvent.RetryRequested, OutboxEvent.Receipt(MessageStatus.DELIVERED)
        )
        for (event in events) assertSame(read, read.reduce(event, policy), "READ then $event")
    }

    @Test
    fun should_end_at_the_strongest_receipt_whatever_the_order() {
        val orders = listOf(
            listOf(OutboxEvent.Acked(1), OutboxEvent.Receipt(MessageStatus.DELIVERED), OutboxEvent.Receipt(MessageStatus.READ)),
            listOf(OutboxEvent.Receipt(MessageStatus.READ), OutboxEvent.Acked(1), OutboxEvent.Receipt(MessageStatus.DELIVERED)),
            listOf(OutboxEvent.Receipt(MessageStatus.DELIVERED), OutboxEvent.Receipt(MessageStatus.READ), OutboxEvent.Acked(1)),
        )
        for (order in orders) {
            val end = order.fold(message(sending)) { m, e -> m.reduce(e, policy) }
            assertEquals(OutboxState.Read, end.state, "order=$order")
        }
    }

    @Test
    fun should_map_every_state_onto_the_shared_delivery_status() {
        assertEquals(MessageStatus.SENDING, OutboxState.Queued.toMessageStatus())
        assertEquals(MessageStatus.SENDING, sending.toMessageStatus())
        assertEquals(MessageStatus.SENT, OutboxState.Sent(null).toMessageStatus())
        assertEquals(MessageStatus.DELIVERED, OutboxState.Delivered.toMessageStatus())
        assertEquals(MessageStatus.READ, OutboxState.Read.toMessageStatus())
        assertEquals(MessageStatus.FAILED, OutboxState.Failed(SendFailure.Exhausted).toMessageStatus())
    }

    @Test
    fun should_double_the_ack_deadline_per_unanswered_attempt_up_to_the_cap() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 4_000L), (0..3).map { policy.ackTimeoutFor(it) })
    }
}
