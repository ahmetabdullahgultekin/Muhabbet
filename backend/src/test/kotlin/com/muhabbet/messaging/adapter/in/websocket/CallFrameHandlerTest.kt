package com.muhabbet.messaging.adapter.`in`.websocket

import com.muhabbet.messaging.domain.model.CallStatus
import com.muhabbet.messaging.domain.model.CallType
import com.muhabbet.messaging.domain.port.out.UserDisplayInfo
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.messaging.domain.service.CallSignalingService
import com.muhabbet.shared.TestData
import com.muhabbet.shared.model.CallEndReason
import com.muhabbet.shared.protocol.WsMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.UUID

/** Only the two people in a call can end it, and only the callee can answer it. */
class CallFrameHandlerTest {

    private lateinit var calls: CallSignalingService
    private lateinit var sessions: WebSocketSessionManager
    private lateinit var handler: CallFrameHandler

    private val alice = TestData.USER_ID_1
    private val bob = TestData.USER_ID_2
    private val mallory = TestData.USER_ID_3

    @BeforeEach
    fun setUp() {
        val users = mockk<UserDirectoryPort>()
        every { users.findDisplayInfo(any()) } answers {
            firstArg<Collection<UUID>>().associateWith { UserDisplayInfo(it, null, null) }
        }
        calls = CallSignalingService(mockk(relaxed = true), mockk(relaxed = true), users, Clock.systemUTC())
        sessions = mockk(relaxed = true)
        handler = CallFrameHandler(sessions, calls, mockk(relaxed = true), mockk(relaxed = true))
        calls.initiateCall("c1", alice, bob, CallType.VOICE)
    }

    @Test
    fun `should ignore an end frame when the sender is not in the call`() {
        handler.handleEnd(mallory, WsMessage.CallEnd(callId = "c1", reason = CallEndReason.ENDED))

        assertEquals(CallStatus.INITIATED, calls.getCall("c1")?.status)
        verify(exactly = 0) { sessions.sendToUser(any(), any()) }
    }

    @Test
    fun `should end the call when a participant ends it`() {
        handler.handleEnd(bob, WsMessage.CallEnd(callId = "c1", reason = CallEndReason.ENDED))

        assertNull(calls.getCall("c1"))
    }

    @Test
    fun `should ignore an answer when it comes from the caller`() {
        handler.handleAnswer(mockk(relaxed = true), alice, WsMessage.CallAnswer(callId = "c1", accepted = true))

        assertEquals(CallStatus.INITIATED, calls.getCall("c1")?.status)
    }
}
