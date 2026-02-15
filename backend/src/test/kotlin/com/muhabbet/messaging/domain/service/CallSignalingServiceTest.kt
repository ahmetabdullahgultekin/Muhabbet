package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.CallStatus
import com.muhabbet.messaging.domain.model.CallType
import com.muhabbet.messaging.domain.port.out.CallHistoryRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class CallSignalingServiceTest {

    private lateinit var callHistoryRepository: CallHistoryRepository
    private lateinit var service: CallSignalingService

    private val callerId = UUID.randomUUID()
    private val calleeId = UUID.randomUUID()
    private val callId = "call-${UUID.randomUUID()}"

    @BeforeEach
    fun setUp() {
        callHistoryRepository = mockk(relaxed = true)
        service = CallSignalingService(callHistoryRepository)
    }

    @Nested
    inner class InitiateCall {

        @Test
        fun `should create call session when both users are free`() {
            val session = service.initiateCall(callId, callerId, calleeId, CallType.VOICE)

            assertEquals(callId, session.callId)
            assertEquals(callerId, session.callerId)
            assertEquals(calleeId, session.calleeId)
            assertEquals(CallType.VOICE, session.callType)
            assertEquals(CallStatus.INITIATED, session.status)
            assertNotNull(session.startedAt)
            assertNull(session.answeredAt)
            assertNull(session.endedAt)
        }

        @Test
        fun `should store call session for lookup`() {
            service.initiateCall(callId, callerId, calleeId, CallType.VOICE)

            val retrieved = service.getCall(callId)
            assertNotNull(retrieved)
            assertEquals(callId, retrieved!!.callId)
        }

        @Test
        fun `should track active call for both users`() {
            service.initiateCall(callId, callerId, calleeId, CallType.VOICE)

            assertEquals(callId, service.getActiveCallForUser(callerId))
            assertEquals(callId, service.getActiveCallForUser(calleeId))
        }

        @Test
        fun `should throw when caller is already in a call`() {
            val otherUser = UUID.randomUUID()
            service.initiateCall(callId, callerId, otherUser, CallType.VOICE)

            assertThrows<CallBusyException> {
                service.initiateCall("call-2", callerId, calleeId, CallType.VOICE)
            }
        }

        @Test
        fun `should throw when callee is already in a call`() {
            val otherUser = UUID.randomUUID()
            service.initiateCall(callId, otherUser, calleeId, CallType.VOICE)

            assertThrows<CallBusyException> {
                service.initiateCall("call-2", callerId, calleeId, CallType.VOICE)
            }
        }

        @Test
        fun `should support video call type`() {
            val session = service.initiateCall(callId, callerId, calleeId, CallType.VIDEO)

            assertEquals(CallType.VIDEO, session.callType)
        }
    }

    @Nested
    inner class AnswerCall {

        @Test
        fun `should transition call to answered state`() {
            service.initiateCall(callId, callerId, calleeId, CallType.VOICE)

            val answered = service.answerCall(callId)

            assertNotNull(answered)
            assertEquals(CallStatus.ANSWERED, answered!!.status)
            assertNotNull(answered.answeredAt)
        }

        @Test
        fun `should return null when call does not exist`() {
            val result = service.answerCall("nonexistent-call")

            assertNull(result)
        }
    }

    @Nested
    inner class EndCall {

        @Test
        fun `should end call with default ENDED status`() {
            service.initiateCall(callId, callerId, calleeId, CallType.VOICE)

            val ended = service.endCall(callId)

            assertNotNull(ended)
            assertEquals(CallStatus.ENDED, ended!!.status)
            assertNotNull(ended.endedAt)
        }

        @Test
        fun `should end call with DECLINED status`() {
            service.initiateCall(callId, callerId, calleeId, CallType.VOICE)

            val ended = service.endCall(callId, CallStatus.DECLINED)

            assertNotNull(ended)
            assertEquals(CallStatus.DECLINED, ended!!.status)
        }

        @Test
        fun `should end call with MISSED status`() {
            service.initiateCall(callId, callerId, calleeId, CallType.VOICE)

            val ended = service.endCall(callId, CallStatus.MISSED)

            assertNotNull(ended)
            assertEquals(CallStatus.MISSED, ended!!.status)
        }

        @Test
        fun `should remove call from active calls`() {
            service.initiateCall(callId, callerId, calleeId, CallType.VOICE)
            service.endCall(callId)

            assertNull(service.getCall(callId))
        }

        @Test
        fun `should clear active call mappings for both users`() {
            service.initiateCall(callId, callerId, calleeId, CallType.VOICE)
            service.endCall(callId)

            assertNull(service.getActiveCallForUser(callerId))
            assertNull(service.getActiveCallForUser(calleeId))
        }

        @Test
        fun `should persist call history on end`() {
            service.initiateCall(callId, callerId, calleeId, CallType.VOICE)
            service.answerCall(callId)
            service.endCall(callId)

            val historySlot = slot<CallHistoryRecord>()
            verify { callHistoryRepository.save(capture(historySlot)) }

            val history = historySlot.captured
            assertEquals(callId, history.callId)
            assertEquals(callerId, history.callerId)
            assertEquals(calleeId, history.calleeId)
            assertEquals("VOICE", history.callType)
            assertEquals("ENDED", history.status)
            assertNotNull(history.answeredAt)
            assertNotNull(history.endedAt)
            assertNotNull(history.durationSeconds)
        }

        @Test
        fun `should persist null duration when call was not answered`() {
            service.initiateCall(callId, callerId, calleeId, CallType.VOICE)
            service.endCall(callId, CallStatus.MISSED)

            val historySlot = slot<CallHistoryRecord>()
            verify { callHistoryRepository.save(capture(historySlot)) }

            val history = historySlot.captured
            assertNull(history.answeredAt)
            assertNull(history.durationSeconds)
        }

        @Test
        fun `should return null when ending nonexistent call`() {
            val result = service.endCall("nonexistent-call")

            assertNull(result)
        }

        @Test
        fun `should allow users to start new calls after ending`() {
            service.initiateCall(callId, callerId, calleeId, CallType.VOICE)
            service.endCall(callId)

            val newCallId = "call-new"
            val newSession = service.initiateCall(newCallId, callerId, calleeId, CallType.VIDEO)

            assertNotNull(newSession)
            assertEquals(newCallId, newSession.callId)
        }
    }

    @Nested
    inner class GetOtherParty {

        @Test
        fun `should return callee when given caller`() {
            service.initiateCall(callId, callerId, calleeId, CallType.VOICE)

            val other = service.getOtherParty(callId, callerId)

            assertEquals(calleeId, other)
        }

        @Test
        fun `should return caller when given callee`() {
            service.initiateCall(callId, callerId, calleeId, CallType.VOICE)

            val other = service.getOtherParty(callId, calleeId)

            assertEquals(callerId, other)
        }

        @Test
        fun `should return null when user is not part of call`() {
            service.initiateCall(callId, callerId, calleeId, CallType.VOICE)

            val other = service.getOtherParty(callId, UUID.randomUUID())

            assertNull(other)
        }

        @Test
        fun `should return null when call does not exist`() {
            val other = service.getOtherParty("nonexistent", callerId)

            assertNull(other)
        }
    }

    @Nested
    inner class ConcurrentCalls {

        @Test
        fun `should support multiple independent calls`() {
            val user1 = UUID.randomUUID()
            val user2 = UUID.randomUUID()
            val user3 = UUID.randomUUID()
            val user4 = UUID.randomUUID()

            val call1 = "call-1"
            val call2 = "call-2"

            service.initiateCall(call1, user1, user2, CallType.VOICE)
            service.initiateCall(call2, user3, user4, CallType.VIDEO)

            assertNotNull(service.getCall(call1))
            assertNotNull(service.getCall(call2))
            assertEquals(call1, service.getActiveCallForUser(user1))
            assertEquals(call2, service.getActiveCallForUser(user3))
        }

        @Test
        fun `should handle history persistence failure gracefully`() {
            every { callHistoryRepository.save(any()) } throws RuntimeException("DB error")

            service.initiateCall(callId, callerId, calleeId, CallType.VOICE)

            // Should not throw — persistence failure is logged, not propagated
            val ended = service.endCall(callId)
            assertNotNull(ended)
        }
    }
}
