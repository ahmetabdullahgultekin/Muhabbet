package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.HistoryWindow
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class HistoryAccessTest {

    private val conversations = mockk<ConversationRepository>()
    private val access = HistoryAccess(conversations)

    private val convId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val joinedAt = Instant.parse("2026-11-13T12:00:00Z")

    private fun given(type: ConversationType) {
        every { conversations.findMember(convId, userId) } returns
            ConversationMember(conversationId = convId, userId = userId, joinedAt = joinedAt)
        every { conversations.findById(convId) } returns Conversation(id = convId, type = type)
    }

    private fun messageAt(ts: Instant) =
        Message(
            id = UUID.randomUUID(),
            conversationId = convId,
            senderId = UUID.randomUUID(),
            content = "x",
            serverTimestamp = ts,
            clientTimestamp = ts
        )

    @Test
    fun `should refuse a non-member before anything else`() {
        every { conversations.findMember(convId, userId) } returns null
        val ex = assertThrows<BusinessException> { access.windowFor(convId, userId) }
        assertEquals(ErrorCode.MSG_NOT_MEMBER, ex.errorCode)
    }

    @Test
    fun `should give a group member the window their membership opens`() {
        given(ConversationType.GROUP)
        assertEquals(HistoryWindow.From(joinedAt), access.windowFor(convId, userId))
    }

    @Test
    fun `should report a pre-join group message as not found rather than forbidden`() {
        given(ConversationType.GROUP)
        val ex = assertThrows<BusinessException> { access.requireVisible(messageAt(joinedAt.minusSeconds(1)), userId) }
        assertEquals(ErrorCode.MSG_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `should use the caller's not-found code`() {
        given(ConversationType.GROUP)
        val ex = assertThrows<BusinessException> {
            access.requireVisible(messageAt(joinedAt.minusSeconds(1)), userId, notVisible = ErrorCode.POLL_MESSAGE_NOT_FOUND)
        }
        assertEquals(ErrorCode.POLL_MESSAGE_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `should allow an older direct message`() {
        given(ConversationType.DIRECT)
        assertDoesNotThrow { access.requireVisible(messageAt(joinedAt.minusSeconds(3600)), userId) }
    }

    @Test
    fun `should not look the conversation up when the message is after the join`() {
        given(ConversationType.GROUP)
        access.requireVisible(messageAt(joinedAt.plusSeconds(1)), userId)
        verify(exactly = 0) { conversations.findById(any()) }
    }
}
