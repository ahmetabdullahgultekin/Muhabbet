package com.muhabbet.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #879: the delivery status must be a join over a lattice, not whatever arrived last.
 *
 * The permutation test is the property the issue asks for, checked exhaustively rather than by
 * sampling: every ordering of every sequence of server events up to length four, from both
 * unconfirmed starting points.
 */
class MessageStatusTransitionsTest {

    private val serverEvents = listOf(MessageStatus.SENT, MessageStatus.DELIVERED, MessageStatus.READ)

    @Test
    fun should_end_at_the_strongest_event_whatever_order_the_events_arrive_in() {
        for (start in listOf(MessageStatus.SENDING, MessageStatus.FAILED)) {
            for (events in sequencesUpTo(4)) {
                val expected = events.maxByOrNull { it.deliveryRank } ?: start
                for (order in permutations(events)) {
                    val result = order.fold(start) { status, event -> status.advance(event) }
                    assertEquals(expected, result, "start=$start order=$order")
                }
            }
        }
    }

    @Test
    fun should_never_regress_read() {
        for (event in MessageStatus.entries) {
            assertEquals(MessageStatus.READ, MessageStatus.READ.advance(event), "READ then $event")
        }
    }

    @Test
    fun should_not_let_a_late_delivered_turn_read_back_into_delivered() {
        assertEquals(
            MessageStatus.READ,
            MessageStatus.SENT.advance(MessageStatus.READ).advance(MessageStatus.DELIVERED)
        )
    }

    @Test
    fun should_not_let_a_late_sent_ack_regress_a_delivered_message() {
        assertEquals(MessageStatus.DELIVERED, MessageStatus.DELIVERED.advance(MessageStatus.SENT))
    }

    @Test
    fun should_fail_only_while_unconfirmed() {
        assertEquals(MessageStatus.FAILED, MessageStatus.SENDING.advance(MessageStatus.FAILED))
        assertEquals(MessageStatus.SENT, MessageStatus.SENT.advance(MessageStatus.FAILED))
        assertEquals(MessageStatus.DELIVERED, MessageStatus.DELIVERED.advance(MessageStatus.FAILED))
        assertEquals(MessageStatus.READ, MessageStatus.READ.advance(MessageStatus.FAILED))
    }

    @Test
    fun should_accept_a_late_ack_as_proof_that_a_failed_message_went() {
        assertEquals(MessageStatus.SENT, MessageStatus.FAILED.advance(MessageStatus.SENT))
    }

    @Test
    fun should_leave_failed_only_through_retry() {
        assertEquals(MessageStatus.FAILED, MessageStatus.FAILED.advance(MessageStatus.SENDING))
        assertEquals(MessageStatus.SENDING, MessageStatus.FAILED.retry())
        for (other in MessageStatus.entries - MessageStatus.FAILED) {
            assertEquals(other, other.retry(), "retry must not touch $other")
        }
    }

    private fun sequencesUpTo(length: Int): List<List<MessageStatus>> {
        val out = mutableListOf<List<MessageStatus>>(emptyList())
        var frontier = listOf<List<MessageStatus>>(emptyList())
        repeat(length) {
            frontier = frontier.flatMap { prefix -> serverEvents.map { prefix + it } }
            out += frontier
        }
        return out
    }

    private fun <T> permutations(items: List<T>): List<List<T>> =
        if (items.size <= 1) {
            listOf(items)
        } else {
            items.indices.flatMap { i ->
                permutations(items.take(i) + items.drop(i + 1)).map { listOf(items[i]) + it }
            }
        }
}
