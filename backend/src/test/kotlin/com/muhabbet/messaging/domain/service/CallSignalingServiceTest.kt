package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.adapter.out.external.InMemoryCallSessionStore
import com.muhabbet.messaging.domain.model.CallEvent
import com.muhabbet.messaging.domain.model.CallHistoryRecord
import com.muhabbet.messaging.domain.model.CallOutcome
import com.muhabbet.messaging.domain.model.CallRefusal
import com.muhabbet.messaging.domain.model.CallSession
import com.muhabbet.messaging.domain.model.CallState
import com.muhabbet.messaging.domain.model.CallTransition
import com.muhabbet.messaging.domain.model.CallType
import com.muhabbet.messaging.domain.port.`in`.CallPlacement
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.CallHistoryRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.provider.Arguments
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

/** #878: the call lifecycle as a state machine, its actors, its store and its ring timeout. */
class CallSignalingServiceTest {

    private val start = Instant.parse("2026-11-13T12:00:00Z")
    private val caller = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val callee = UUID.fromString("00000000-0000-0000-0000-00000000000b")
    private val outsider = UUID.fromString("00000000-0000-0000-0000-00000000000c")

    private val history = mutableListOf<CallHistoryRecord>()
    private val callHistoryRepository = mockk<CallHistoryRepository>()
    private val blockPolicy = mockk<BlockPolicyPort>()
    private val clock = MutableClock(start)
    private lateinit var service: CallSignalingService

    @BeforeEach
    fun setUp() {
        every { callHistoryRepository.save(any()) } answers { firstArg<CallHistoryRecord>().also(history::add) }
        every { blockPolicy.hasBlocked(any(), any()) } returns false
        service = CallSignalingService(InMemoryCallSessionStore(), callHistoryRepository, blockPolicy, clock)
    }

    private fun ring(callId: String = "call-1", from: UUID = caller, to: UUID = callee): CallSession =
        (service.placeCall(callId, from, to, CallType.VOICE) as CallPlacement.Ringing).session

    // ─── The pure transition table ───────────────────────

    @Nested
    inner class Transitions {

        private val ringing = CallSession("c", caller, callee, CallType.VOICE, start)
        private val active = ringing.copy(state = CallState.Active(start.plusSeconds(5)))
        private val ended = ringing.copy(state = CallState.Ended(CallOutcome.ENDED, start.plusSeconds(9)))
        private val now = start.plusSeconds(10)

        private fun table(): Stream<Arguments> = Stream.of(
            // state, event, actor, expected (a CallState for a move, a CallRefusal for a refusal)
            Arguments.of(ringing, CallEvent.Answer, callee, CallState.Active(now)),
            Arguments.of(ringing, CallEvent.Answer, caller, CallRefusal.WRONG_ACTOR),
            Arguments.of(ringing, CallEvent.Answer, outsider, CallRefusal.NOT_PARTICIPANT),
            Arguments.of(ringing, CallEvent.Decline, callee, CallState.Ended(CallOutcome.DECLINED, now)),
            Arguments.of(ringing, CallEvent.Decline, caller, CallRefusal.WRONG_ACTOR),
            Arguments.of(ringing, CallEvent.Hangup, caller, CallState.Ended(CallOutcome.MISSED, now)),
            Arguments.of(ringing, CallEvent.Hangup, callee, CallState.Ended(CallOutcome.DECLINED, now)),
            Arguments.of(ringing, CallEvent.Hangup, outsider, CallRefusal.NOT_PARTICIPANT),
            Arguments.of(ringing, CallEvent.RingTimeout, null, CallState.Ended(CallOutcome.MISSED, now)),
            Arguments.of(ringing, CallEvent.RingTimeout, caller, CallRefusal.WRONG_ACTOR),
            Arguments.of(active, CallEvent.Hangup, caller, CallState.Ended(CallOutcome.ENDED, now, start.plusSeconds(5))),
            Arguments.of(active, CallEvent.Hangup, callee, CallState.Ended(CallOutcome.ENDED, now, start.plusSeconds(5))),
            Arguments.of(active, CallEvent.Hangup, outsider, CallRefusal.NOT_PARTICIPANT),
            Arguments.of(active, CallEvent.Answer, callee, CallRefusal.ILLEGAL_TRANSITION),
            Arguments.of(active, CallEvent.Decline, callee, CallRefusal.ILLEGAL_TRANSITION),
            Arguments.of(active, CallEvent.RingTimeout, null, CallRefusal.ILLEGAL_TRANSITION),
            Arguments.of(ended, CallEvent.Answer, callee, CallRefusal.ILLEGAL_TRANSITION),
            Arguments.of(ended, CallEvent.Hangup, caller, CallRefusal.ILLEGAL_TRANSITION),
            Arguments.of(ended, CallEvent.RingTimeout, null, CallRefusal.ILLEGAL_TRANSITION),
            Arguments.of(ended, CallEvent.Hangup, outsider, CallRefusal.NOT_PARTICIPANT)
        )

        @Test
        fun `should apply every row of the transition table`() {
            table().forEach { row ->
                val (from, event, actor, expected) = row.get().let { arrayOf(it[0], it[1], it[2], it[3]) }
                val result = (from as CallSession).on(event as CallEvent, actor as UUID?, now)
                val label = "${from.state} + $event by $actor"
                when (expected) {
                    is CallState -> assertEquals(CallTransition.Moved(from.copy(state = expected)), result, label)
                    is CallRefusal -> assertEquals(CallTransition.Refused(expected), result, label)
                    else -> error("bad row")
                }
            }
        }

        @Test
        fun `should record the talk time of an answered call`() {
            val endedAfterTalk = (active.on(CallEvent.Hangup, caller, now) as CallTransition.Moved).session

            val record = endedAfterTalk.toHistory()
            assertEquals(CallOutcome.ENDED, record?.status)
            assertEquals(5, record?.durationSeconds)
        }

        @Test
        fun `should have no history row while a call is still going`() {
            assertNull(ringing.toHistory())
            assertNull(active.toHistory())
        }
    }

    // ─── Placing ─────────────────────────────────────────

    @Nested
    inner class Placing {

        @Test
        fun `should ring the callee`() {
            val session = ring()

            assertEquals(CallState.Ringing, session.state)
            assertEquals(caller, service.otherParty("call-1", callee))
        }

        @Test
        fun `should report BUSY when the callee is already in a call`() {
            ring("call-1", caller, callee)

            assertEquals(CallPlacement.Busy, service.placeCall("call-2", outsider, callee, CallType.VOICE))
        }

        @Test
        fun `should refuse a call id that is already in use instead of overwriting the live call`() {
            ring("call-1", caller, callee)

            val ex = assertThrows<BusinessException> { service.placeCall("call-1", outsider, UUID.randomUUID(), CallType.VOICE) }

            assertEquals(ErrorCode.CALL_INVALID_TARGET, ex.errorCode)
            assertEquals(caller, service.otherParty("call-1", callee), "the live call must be untouched")
        }

        @Test
        fun `should refuse a call to oneself`() {
            val ex = assertThrows<BusinessException> { service.placeCall("c", caller, caller, CallType.VOICE) }

            assertEquals(ErrorCode.CALL_INVALID_TARGET, ex.errorCode)
        }

        @Test
        fun `should not ring a callee who has blocked the caller`() {
            every { blockPolicy.hasBlocked(callee, caller) } returns true

            assertEquals(CallPlacement.Unreachable, service.placeCall("c", caller, callee, CallType.VOICE))
            assertNull(service.otherParty("c", caller), "nothing was reserved")
        }

        @Test
        fun `should not ring someone the caller has blocked`() {
            every { blockPolicy.hasBlocked(caller, callee) } returns true

            assertEquals(CallPlacement.Unreachable, service.placeCall("c", caller, callee, CallType.VOICE))
        }

        @Test
        fun `should ring exactly one of two concurrent callers of the same callee`() {
            val pool = Executors.newFixedThreadPool(8)
            val gate = CountDownLatch(1)
            val callers = List(8) { UUID.randomUUID() }
            val results = callers.mapIndexed { i, from ->
                pool.submit<CallPlacement> {
                    gate.await()
                    service.placeCall("race-$i", from, callee, CallType.VOICE)
                }
            }
            gate.countDown()
            val placements = results.map { it.get(10, TimeUnit.SECONDS) }
            pool.shutdown()

            assertEquals(1, placements.count { it is CallPlacement.Ringing })
            assertEquals(7, placements.count { it == CallPlacement.Busy })
        }
    }

    // ─── Conducting ──────────────────────────────────────

    @Nested
    inner class Conducting {

        @Test
        fun `should let the callee answer`() {
            ring()

            assertInstanceOf(CallState.Active::class.java, service.respond("call-1", callee, accepted = true).state)
        }

        @Test
        fun `should not let the caller answer their own call`() {
            ring()

            val ex = assertThrows<BusinessException> { service.respond("call-1", caller, accepted = true) }

            assertEquals(ErrorCode.CALL_INVALID_STATE, ex.errorCode)
        }

        @Test
        fun `should not re-answer an answered call`() {
            ring()
            service.respond("call-1", callee, accepted = true)

            val ex = assertThrows<BusinessException> { service.respond("call-1", callee, accepted = true) }

            assertEquals(ErrorCode.CALL_INVALID_STATE, ex.errorCode)
        }

        @Test
        fun `should treat a non-participant ending a call as an unknown call and write no history`() {
            ring()

            val ex = assertThrows<BusinessException> { service.hangUp("call-1", outsider) }

            assertEquals(ErrorCode.CALL_NOT_FOUND, ex.errorCode)
            assertEquals(caller, service.otherParty("call-1", callee), "the call is still live")
            verify(exactly = 0) { callHistoryRepository.save(any()) }
        }

        @Test
        fun `should treat a non-participant answering as an unknown call`() {
            ring()

            val ex = assertThrows<BusinessException> { service.respond("call-1", outsider, accepted = true) }

            assertEquals(ErrorCode.CALL_NOT_FOUND, ex.errorCode)
        }

        @Test
        fun `should give an outsider no party to relay signalling to`() {
            ring()

            assertNull(service.otherParty("call-1", outsider))
        }

        @Test
        fun `should end an answered call, free both users and write ENDED with its duration`() {
            ring()
            service.respond("call-1", callee, accepted = true)
            clock.advance(30)

            service.hangUp("call-1", caller)

            assertEquals(CallOutcome.ENDED, history.single().status)
            assertEquals(30, history.single().durationSeconds)
            assertEquals(CallType.VOICE, history.single().callType)
            assertTrue(service.placeCall("call-2", caller, callee, CallType.VIDEO) is CallPlacement.Ringing)
        }

        @Test
        fun `should record a declined call`() {
            ring()

            service.respond("call-1", callee, accepted = false)

            assertEquals(CallOutcome.DECLINED, history.single().status)
        }

        @Test
        fun `should not end a call twice`() {
            ring()
            service.hangUp("call-1", caller)

            val ex = assertThrows<BusinessException> { service.hangUp("call-1", callee) }

            assertEquals(ErrorCode.CALL_NOT_FOUND, ex.errorCode)
            assertEquals(1, history.size)
        }
    }

    // ─── Ring timeout ────────────────────────────────────

    @Nested
    inner class RingTimeout {

        @Test
        fun `should leave a call ringing before the timeout`() {
            ring()
            clock.advance(CallSignalingService.RING_TIMEOUT_SECONDS - 1)

            assertTrue(service.expireUnansweredCalls().isEmpty())
        }

        @Test
        fun `should end an unanswered call as MISSED after the timeout and free both users`() {
            ring()
            clock.advance(CallSignalingService.RING_TIMEOUT_SECONDS)

            val expired = service.expireUnansweredCalls()

            assertEquals(listOf("call-1"), expired.map { it.callId })
            assertEquals(CallOutcome.MISSED, history.single().status)
            assertTrue(service.placeCall("call-2", callee, caller, CallType.VOICE) is CallPlacement.Ringing)
        }

        @Test
        fun `should not expire an answered call`() {
            ring()
            service.respond("call-1", callee, accepted = true)
            clock.advance(CallSignalingService.RING_TIMEOUT_SECONDS * 10)

            assertTrue(service.expireUnansweredCalls().isEmpty())
        }
    }

    /** A clock a test can move forward. */
    private class MutableClock(private var now: Instant) : Clock() {
        fun advance(seconds: Long) { now = now.plusSeconds(seconds) }
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }
}
