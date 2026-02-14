package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.CallStatus
import com.muhabbet.messaging.domain.model.CallType
import com.muhabbet.messaging.domain.port.out.CallHistoryRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

class CallSignalingServiceTest {

    private val callHistoryRepository = mockk<CallHistoryRepository>(relaxed = true)
    private lateinit var service: CallSignalingService

    @BeforeEach
    fun setUp() {
        service = CallSignalingService(callHistoryRepository)
    }

    @Test
    fun `should initiate call successfully`() {
        val callId = UUID.randomUUID().toString()
        val callerId = UUID.randomUUID()
        val calleeId = UUID.randomUUID()

        assertDoesNotThrow {
            service.initiateCall(callId, callerId, calleeId, CallType.VOICE)
        }
    }

    @Test
    fun `should get call session after initiation`() {
        val callId = UUID.randomUUID().toString()
        val callerId = UUID.randomUUID()
        val calleeId = UUID.randomUUID()

        service.initiateCall(callId, callerId, calleeId, CallType.VOICE)
        val session = service.getCall(callId)
        assertNotNull(session)
    }

    @Test
    fun `should throw CallBusyException when user is already in call`() {
        val callId1 = UUID.randomUUID().toString()
        val callId2 = UUID.randomUUID().toString()
        val callerId = UUID.randomUUID()
        val calleeId = UUID.randomUUID()
        val otherUser = UUID.randomUUID()

        service.initiateCall(callId1, callerId, calleeId, CallType.VOICE)

        assertThrows(CallBusyException::class.java) {
            service.initiateCall(callId2, callerId, otherUser, CallType.VOICE)
        }
    }

    @Test
    fun `should get other party in call`() {
        val callId = UUID.randomUUID().toString()
        val callerId = UUID.randomUUID()
        val calleeId = UUID.randomUUID()

        service.initiateCall(callId, callerId, calleeId, CallType.VOICE)
        val otherParty = service.getOtherParty(callId, callerId)
        assertEquals(calleeId, otherParty)
    }

    @Test
    fun `should end call and persist history`() {
        val callId = UUID.randomUUID().toString()
        val callerId = UUID.randomUUID()
        val calleeId = UUID.randomUUID()

        service.initiateCall(callId, callerId, calleeId, CallType.VOICE)
        service.endCall(callId, CallStatus.ENDED)

        verify { callHistoryRepository.save(any()) }
        assertNull(service.getCall(callId))
    }

    @Test
    fun `should answer call and update status`() {
        val callId = UUID.randomUUID().toString()
        val callerId = UUID.randomUUID()
        val calleeId = UUID.randomUUID()

        service.initiateCall(callId, callerId, calleeId, CallType.VOICE)
        service.answerCall(callId)

        val session = service.getCall(callId)
        assertNotNull(session)
    }

    @Test
    fun `should return null for non-existent call`() {
        assertNull(service.getCall("nonexistent"))
        assertNull(service.getOtherParty("nonexistent", UUID.randomUUID()))
    }
}
