package com.muhabbet.messaging.adapter.`in`.websocket

import com.muhabbet.auth.domain.port.`in`.RecordLastSeenUseCase
import com.muhabbet.messaging.domain.model.Receipt
import com.muhabbet.messaging.domain.port.`in`.RecordReceiptUseCase
import com.muhabbet.messaging.domain.port.`in`.SendMessageUseCase
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.PresencePort
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import com.muhabbet.shared.security.JwtProvider
import com.muhabbet.shared.security.WebSocketRateLimiter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.UUID

/**
 * The `message.ack` frame after #870 / #879: the handler translates and calls one use case.
 *
 * Before, it assembled the read pipeline itself (a conversation-wide bulk update, then a
 * single-message update and broadcast, two transactions), and dropped any status that was not a
 * receipt with a bare `return` — no error, no log.
 */
class ReceiptFrameTest {

    private val recordReceiptUseCase: RecordReceiptUseCase = mockk(relaxed = true)
    private val sessionManager: WebSocketSessionManager = mockk(relaxed = true)
    private val rateLimiter: WebSocketRateLimiter = mockk()
    private val session: WebSocketSession = mockk(relaxed = true)

    private val readerId = UUID.randomUUID()
    private val conversationId = UUID.randomUUID()
    private val messageId = UUID.randomUUID()

    private val handler = ChatWebSocketHandler(
        jwtProvider = mockk<JwtProvider>(relaxed = true),
        sessionManager = sessionManager,
        sendMessageUseCase = mockk<SendMessageUseCase>(relaxed = true),
        recordReceiptUseCase = recordReceiptUseCase,
        conversationRepository = mockk<ConversationRepository>(relaxed = true),
        presencePort = mockk<PresencePort>(relaxed = true),
        recordLastSeenUseCase = mockk<RecordLastSeenUseCase>(relaxed = true),
        callFrameHandler = mockk<CallFrameHandler>(relaxed = true),
        webSocketRateLimiter = rateLimiter,
        presenceVisibility = PresenceVisibility(
            mockk<BlockPolicyPort>(relaxed = true),
            mockk<ConversationRepository>(relaxed = true)
        )
    )

    @BeforeEach
    fun setUp() {
        every { rateLimiter.allowMessage(any()) } returns true
        every { session.attributes } returns mutableMapOf<String, Any>("userId" to readerId)
        every { session.isOpen } returns true
        every { session.id } returns "s-1"
    }

    private fun ack(status: MessageStatus): List<String> {
        val sent = mutableListOf<String>()
        every { sessionManager.send(session, capture(sent)) } returns Unit
        val frame = WsMessage.AckMessage(
            messageId = messageId.toString(),
            conversationId = conversationId.toString(),
            status = status
        )
        handler.handleMessage(session, TextMessage(wsJson.encodeToString<WsMessage>(frame)))
        return sent
    }

    @Test
    fun `should record a DELIVERED receipt for the acked message`() {
        val sent = ack(MessageStatus.DELIVERED)

        verify(exactly = 1) { recordReceiptUseCase.record(Receipt.Delivered(messageId, readerId)) }
        assertTrue(sent.isEmpty(), "a receipt has no reply frame")
    }

    @Test
    fun `should record READ as read-up-to the acked message in its conversation, in one call`() {
        ack(MessageStatus.READ)

        // One use-case call. The handler used to call markConversationRead and then updateStatus,
        // two transactions, with the read pipeline's shape decided here in the adapter.
        verify(exactly = 1) { recordReceiptUseCase.record(Receipt.Read(conversationId, messageId, readerId)) }
        verify(exactly = 1) { recordReceiptUseCase.record(any()) }
    }

    @ParameterizedTest
    @EnumSource(value = MessageStatus::class, names = ["SENDING", "SENT"])
    fun `should answer a non-receipt status with a typed error and record nothing`(status: MessageStatus) {
        val sent = ack(status)

        val error = wsJson.decodeFromString<WsMessage>(sent.single()) as WsMessage.Error
        assertEquals(ErrorCode.MSG_INVALID_RECEIPT_STATUS.name, error.code)
        verify(exactly = 0) { recordReceiptUseCase.record(any()) }
        verify(exactly = 0) { session.close(any()) }
    }
}
