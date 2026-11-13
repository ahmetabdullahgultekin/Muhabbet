package com.muhabbet.messaging.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.UUID
import java.util.stream.Stream

/**
 * The delivery lifecycle as a pure state machine (#870) and the sender aggregate (#871).
 *
 * Exhaustive rather than illustrative: three states, so the whole transition table is nine rows
 * and every one of them is asserted.
 */
class DeliveryStatusStateMachineTest {

    companion object {
        @JvmStatic
        fun transitionTable(): Stream<Arguments> = Stream.of(
            // stored,                    receipt,                  applied?
            Arguments.of(DeliveryStatus.SENT, DeliveryStatus.SENT, false),
            Arguments.of(DeliveryStatus.SENT, DeliveryStatus.DELIVERED, true),
            Arguments.of(DeliveryStatus.SENT, DeliveryStatus.READ, true),
            Arguments.of(DeliveryStatus.DELIVERED, DeliveryStatus.SENT, false),
            Arguments.of(DeliveryStatus.DELIVERED, DeliveryStatus.DELIVERED, false),
            Arguments.of(DeliveryStatus.DELIVERED, DeliveryStatus.READ, true),
            Arguments.of(DeliveryStatus.READ, DeliveryStatus.SENT, false),
            Arguments.of(DeliveryStatus.READ, DeliveryStatus.DELIVERED, false),
            Arguments.of(DeliveryStatus.READ, DeliveryStatus.READ, false)
        )

        /** Every ordering of a list — 3 receipts give 6, small enough to enumerate outright. */
        private fun <T> permutations(items: List<T>): List<List<T>> =
            if (items.size <= 1) listOf(items)
            else items.indices.flatMap { i ->
                permutations(items.filterIndexed { j, _ -> j != i }).map { listOf(items[i]) + it }
            }
    }

    @ParameterizedTest(name = "{0} + receipt {1} -> applied={2}")
    @MethodSource("transitionTable")
    fun `should follow the transition table`(stored: DeliveryStatus, receipt: DeliveryStatus, applied: Boolean) {
        val transition = stored.advance(receipt)

        if (applied) {
            assertEquals(DeliveryTransition.Applied(from = stored, to = receipt), transition)
        } else {
            assertEquals(DeliveryTransition.Ignored(current = stored), transition)
        }
    }

    @Test
    fun `should ignore a late DELIVERED after READ`() {
        // The defect in one line: the slow REST DELIVERED (#596) landing after the WebSocket READ.
        assertEquals(DeliveryTransition.Ignored(DeliveryStatus.READ), DeliveryStatus.READ.advance(DeliveryStatus.DELIVERED))
    }

    @Test
    fun `should make advancesFrom exactly the set of states advance applies from`() {
        // advancesFrom is what the store puts in its WHERE clause. If it ever drifted from advance,
        // the database would enforce a different lifecycle from the one this file tests.
        DeliveryStatus.entries.forEach { to ->
            val expected = DeliveryStatus.entries.filter { it.advance(to) is DeliveryTransition.Applied }.toSet()
            assertEquals(expected, to.advancesFrom(), "advancesFrom($to)")
        }
        assertEquals(emptySet<DeliveryStatus>(), DeliveryStatus.SENT.advancesFrom())
        assertEquals(setOf(DeliveryStatus.SENT), DeliveryStatus.DELIVERED.advancesFrom())
        assertEquals(setOf(DeliveryStatus.SENT, DeliveryStatus.DELIVERED), DeliveryStatus.READ.advancesFrom())
    }

    @Test
    fun `should end at the furthest receipt whatever order receipts arrive in`() {
        // Property over every permutation: folding receipts through advance from SENT always ends
        // at the maximum, and the state never moves backwards on the way.
        val receipts = listOf(DeliveryStatus.SENT, DeliveryStatus.DELIVERED, DeliveryStatus.READ)
        permutations(receipts).forEach { order ->
            var state = DeliveryStatus.SENT
            order.forEach { receipt ->
                val before = state
                state = when (val t = state.advance(receipt)) {
                    is DeliveryTransition.Applied -> t.to
                    is DeliveryTransition.Ignored -> t.current
                }
                assertTrue(state >= before, "regressed from $before to $state in $order")
            }
            assertEquals(DeliveryStatus.READ, state, "order $order")
        }
    }

    @Test
    fun `should be idempotent for a repeated receipt`() {
        DeliveryStatus.entries.forEach { receipt ->
            val once = when (val t = DeliveryStatus.SENT.advance(receipt)) {
                is DeliveryTransition.Applied -> t.to
                is DeliveryTransition.Ignored -> t.current
            }
            assertEquals(DeliveryTransition.Ignored(once), once.advance(receipt))
        }
    }

    // ─── Aggregate (#871) ────────────────────────────────────

    @Test
    fun `should aggregate a 1-to-1 chat to the single recipient's state`() {
        DeliveryStatus.entries.forEach { assertEquals(it, DeliveryStatus.aggregate(listOf(it))) }
    }

    @Test
    fun `should aggregate a group to the minimum across recipients`() {
        val r = DeliveryStatus.READ
        val d = DeliveryStatus.DELIVERED
        val s = DeliveryStatus.SENT
        assertEquals(s, DeliveryStatus.aggregate(listOf(r, d, s)))
        assertEquals(d, DeliveryStatus.aggregate(listOf(r, r, d)), "one member has not read it")
        assertEquals(r, DeliveryStatus.aggregate(listOf(r, r, r)), "everyone has read it")
        assertEquals(s, DeliveryStatus.aggregate(listOf(r, s)), "one member has not received it")
    }

    @Test
    fun `should aggregate no recipients to SENT`() {
        assertEquals(DeliveryStatus.SENT, DeliveryStatus.aggregate(emptyList()))
    }

    @Test
    fun `should never lower the aggregate when one recipient advances`() {
        // Monotonicity of the aggregate follows from monotonicity per recipient — checked over every
        // 3-member state and every single-member advance from it.
        val states = DeliveryStatus.entries
        for (a in states) for (b in states) for (c in states) {
            val before = DeliveryStatus.aggregate(listOf(a, b, c))
            for (to in states) {
                val advancedA = (a.advance(to) as? DeliveryTransition.Applied)?.to ?: a
                assertTrue(DeliveryStatus.aggregate(listOf(advancedA, b, c)) >= before)
            }
        }
    }

    @Test
    fun `should publish a READ from a reader with receipts off as DELIVERED in the sender view`() {
        val messageId = UUID.randomUUID()
        val optedOut = UUID.randomUUID()
        val optedIn = UUID.randomUUID()
        val rows = listOf(
            MessageDeliveryStatus(messageId, optedOut, DeliveryStatus.READ),
            MessageDeliveryStatus(messageId, optedIn, DeliveryStatus.READ)
        )

        assertEquals(DeliveryStatus.READ, senderView(rows, receiptsDisabled = emptySet()))
        assertEquals(DeliveryStatus.DELIVERED, senderView(rows, receiptsDisabled = setOf(optedOut)))
        // The downgrade touches READ only.
        assertEquals(
            DeliveryStatus.SENT,
            MessageDeliveryStatus(messageId, optedOut, DeliveryStatus.SENT).publishedStatus(setOf(optedOut))
        )
    }
}
