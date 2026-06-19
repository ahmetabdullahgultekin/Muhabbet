package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.PinnedMessage
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.PinnedMessageRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class PinnedMessageServiceTest {

    private val conversationRepository = mockk<ConversationRepository>()
    private val messageRepository = mockk<MessageRepository>()
    private val pinnedMessageRepository = mockk<PinnedMessageRepository>()
    private lateinit var service: PinnedMessageService

    private val conversationId = UUID.randomUUID()
    private val messageId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        service = PinnedMessageService(conversationRepository, messageRepository, pinnedMessageRepository)
    }

    private fun aMessage(convId: UUID = conversationId) = Message(
        id = messageId,
        conversationId = convId,
        senderId = UUID.randomUUID(),
        content = "hello",
        clientTimestamp = Instant.now()
    )

    private fun memberOk() {
        every { conversationRepository.findMember(conversationId, userId) } returns mockk<ConversationMember>()
    }

    @Test
    fun `should pin a message in a conversation the user belongs to`() {
        memberOk()
        every { messageRepository.findById(messageId) } returns aMessage()
        every { pinnedMessageRepository.find(conversationId, messageId) } returns null
        every { pinnedMessageRepository.countByConversationId(conversationId) } returns 1
        every { pinnedMessageRepository.save(any()) } answers { firstArg() }

        val pin = service.pin(conversationId, messageId, userId)

        assertEquals(messageId, pin.messageId)
        assertEquals(userId, pin.pinnedBy)
        verify { pinnedMessageRepository.save(any()) }
    }

    @Test
    fun `should be idempotent and return existing pin without saving`() {
        memberOk()
        every { messageRepository.findById(messageId) } returns aMessage()
        val existing = PinnedMessage(conversationId, messageId, UUID.randomUUID())
        every { pinnedMessageRepository.find(conversationId, messageId) } returns existing

        val pin = service.pin(conversationId, messageId, userId)

        assertEquals(existing, pin)
        verify(exactly = 0) { pinnedMessageRepository.save(any()) }
        verify(exactly = 0) { pinnedMessageRepository.countByConversationId(any()) }
    }

    @Test
    fun `should throw MSG_PIN_LIMIT_REACHED when at the cap`() {
        memberOk()
        every { messageRepository.findById(messageId) } returns aMessage()
        every { pinnedMessageRepository.find(conversationId, messageId) } returns null
        every { pinnedMessageRepository.countByConversationId(conversationId) } returns 3

        val ex = assertThrows<BusinessException> { service.pin(conversationId, messageId, userId) }
        assertEquals(ErrorCode.MSG_PIN_LIMIT_REACHED, ex.errorCode)
        verify(exactly = 0) { pinnedMessageRepository.save(any()) }
    }

    @Test
    fun `should throw MSG_NOT_MEMBER when user is not in the conversation`() {
        every { conversationRepository.findMember(conversationId, userId) } returns null

        val ex = assertThrows<BusinessException> { service.pin(conversationId, messageId, userId) }
        assertEquals(ErrorCode.MSG_NOT_MEMBER, ex.errorCode)
        verify(exactly = 0) { messageRepository.findById(any()) }
    }

    @Test
    fun `should throw MSG_NOT_FOUND when message does not exist`() {
        memberOk()
        every { messageRepository.findById(messageId) } returns null

        val ex = assertThrows<BusinessException> { service.pin(conversationId, messageId, userId) }
        assertEquals(ErrorCode.MSG_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `should throw MSG_NOT_FOUND when message belongs to another conversation`() {
        memberOk()
        every { messageRepository.findById(messageId) } returns aMessage(convId = UUID.randomUUID())

        val ex = assertThrows<BusinessException> { service.pin(conversationId, messageId, userId) }
        assertEquals(ErrorCode.MSG_NOT_FOUND, ex.errorCode)
        verify(exactly = 0) { pinnedMessageRepository.save(any()) }
    }

    @Test
    fun `should unpin for a member`() {
        memberOk()
        every { pinnedMessageRepository.delete(conversationId, messageId) } returns Unit

        service.unpin(conversationId, messageId, userId)

        verify { pinnedMessageRepository.delete(conversationId, messageId) }
    }

    @Test
    fun `should not unpin for a non-member`() {
        every { conversationRepository.findMember(conversationId, userId) } returns null

        assertThrows<BusinessException> { service.unpin(conversationId, messageId, userId) }
        verify(exactly = 0) { pinnedMessageRepository.delete(any(), any()) }
    }

    @Test
    fun `should list pinned messages for a member`() {
        memberOk()
        val pins = listOf(
            PinnedMessage(conversationId, messageId, userId),
            PinnedMessage(conversationId, UUID.randomUUID(), userId)
        )
        every { pinnedMessageRepository.findByConversationId(conversationId) } returns pins

        val result = service.getPinned(conversationId, userId)

        assertEquals(2, result.size)
    }

    @Test
    fun `should not list pinned messages for a non-member`() {
        every { conversationRepository.findMember(conversationId, userId) } returns null

        assertThrows<BusinessException> { service.getPinned(conversationId, userId) }
        verify(exactly = 0) { pinnedMessageRepository.findByConversationId(any()) }
    }
}
