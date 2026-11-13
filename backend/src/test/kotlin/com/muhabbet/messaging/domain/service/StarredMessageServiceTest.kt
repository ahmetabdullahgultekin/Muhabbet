package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.StarredCursor
import com.muhabbet.messaging.domain.model.StarredMessage
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.StarredMessageRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.UUID

class StarredMessageServiceTest {

    private val messageRepository = mockk<MessageRepository>()
    private val conversationRepository = mockk<ConversationRepository>()
    private val starredRepository = mockk<StarredMessageRepository>()
    private lateinit var service: StarredMessageService

    private val userId = UUID.randomUUID()
    private val conversationId = UUID.randomUUID()
    private val messageId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        service = StarredMessageService(messageRepository, conversationRepository, starredRepository)
    }

    private fun aMessage(viewOnce: Boolean = false, isDeleted: Boolean = false) = Message(
        id = messageId,
        conversationId = conversationId,
        senderId = UUID.randomUUID(),
        content = "secret",
        clientTimestamp = Instant.now(),
        viewOnce = viewOnce,
        isDeleted = isDeleted
    )

    private fun star(at: Instant = Instant.now()) =
        StarredMessage(UUID.randomUUID(), at, aMessage().copy(id = UUID.randomUUID()))

    private fun assertRefused(code: ErrorCode, block: () -> Unit) {
        val ex = assertThrows<BusinessException> { block() }
        assertEquals(code, ex.errorCode)
    }

    @Nested
    inner class Star {

        @Test
        fun `should write the bookmark when the caller is a member`() {
            every { messageRepository.findById(messageId) } returns aMessage()
            every { conversationRepository.findMember(conversationId, userId) } returns mockk<ConversationMember>()
            justRun { starredRepository.star(userId, messageId) }

            service.star(userId, messageId)

            verify(exactly = 1) { starredRepository.star(userId, messageId) }
        }

        @Test
        fun `should refuse with MSG_NOT_FOUND and write nothing when the caller is not a member`() {
            every { messageRepository.findById(messageId) } returns aMessage()
            every { conversationRepository.findMember(conversationId, userId) } returns null

            assertRefused(ErrorCode.MSG_NOT_FOUND) { service.star(userId, messageId) }
            verify(exactly = 0) { starredRepository.star(any(), any()) }
        }

        @Test
        fun `should refuse with MSG_NOT_FOUND when the message does not exist`() {
            every { messageRepository.findById(messageId) } returns null

            assertRefused(ErrorCode.MSG_NOT_FOUND) { service.star(userId, messageId) }
            verify(exactly = 0) { starredRepository.star(any(), any()) }
        }

        @Test
        fun `should refuse a view-once message with MSG_NOT_STARRABLE`() {
            every { messageRepository.findById(messageId) } returns aMessage(viewOnce = true)
            every { conversationRepository.findMember(conversationId, userId) } returns mockk<ConversationMember>()

            assertRefused(ErrorCode.MSG_NOT_STARRABLE) { service.star(userId, messageId) }
            verify(exactly = 0) { starredRepository.star(any(), any()) }
        }

        @Test
        fun `should refuse a deleted message with MSG_ALREADY_DELETED`() {
            every { messageRepository.findById(messageId) } returns aMessage(isDeleted = true)
            every { conversationRepository.findMember(conversationId, userId) } returns mockk<ConversationMember>()

            assertRefused(ErrorCode.MSG_ALREADY_DELETED) { service.star(userId, messageId) }
            verify(exactly = 0) { starredRepository.star(any(), any()) }
        }
    }

    @Nested
    inner class GetStarred {

        @ParameterizedTest
        @ValueSource(ints = [0, -1, StarredMessageService.MAX_PAGE_SIZE + 1])
        fun `should reject a limit outside the allowed range with VALIDATION_ERROR`(limit: Int) {
            assertRefused(ErrorCode.VALIDATION_ERROR) { service.getStarred(userId, null, limit) }
            verify(exactly = 0) { starredRepository.findVisibleStarred(any(), any(), any()) }
        }

        @Test
        fun `should reject a malformed cursor with MSG_INVALID_CURSOR`() {
            assertRefused(ErrorCode.MSG_INVALID_CURSOR) { service.getStarred(userId, "nonsense", 10) }
        }

        @Test
        fun `should return a cursor at the last item when there is another page`() {
            val now = Instant.now()
            val rows = listOf(star(now), star(now.minusSeconds(1)), star(now.minusSeconds(2)))
            every { starredRepository.findVisibleStarred(userId, null, 3) } returns rows

            val page = service.getStarred(userId, null, 2)

            assertEquals(rows.take(2), page.items)
            assertTrue(page.hasMore)
            assertEquals(StarredCursor(rows[1].starredAt, rows[1].starId), page.nextCursor)
        }

        @Test
        fun `should pass a decoded cursor through and report the last page`() {
            val cursor = StarredCursor(Instant.parse("2026-11-13T10:00:00Z"), UUID.randomUUID())
            val rows = listOf(star())
            every { starredRepository.findVisibleStarred(userId, cursor, 3) } returns rows

            val page = service.getStarred(userId, cursor.encode(), 2)

            assertEquals(rows, page.items)
            assertFalse(page.hasMore)
            assertNull(page.nextCursor)
        }
    }
}
