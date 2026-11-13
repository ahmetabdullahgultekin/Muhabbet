package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.PinnedMessage
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.PinnedMessageRepository
import com.muhabbet.shared.InlineTransactionRunner
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
    private val messageBroadcaster = mockk<MessageBroadcaster>(relaxed = true)
    private lateinit var service: PinnedMessageService

    private val conversationId = UUID.randomUUID()
    private val messageId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val otherMember = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        service = PinnedMessageService(
            conversationRepository,
            messageRepository,
            pinnedMessageRepository,
            messageBroadcaster,
            InlineTransactionRunner()
        )
        every { conversationRepository.findMembersByConversationId(conversationId) } returns listOf(
            mockk<ConversationMember> { every { userId } returns this@PinnedMessageServiceTest.userId },
            mockk<ConversationMember> { every { userId } returns otherMember }
        )
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
        every { pinnedMessageRepository.findByConversationId(conversationId) } returns emptyList()

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
        every { pinnedMessageRepository.find(conversationId, messageId) } returns
            PinnedMessage(conversationId, messageId, userId)
        every { pinnedMessageRepository.delete(conversationId, messageId) } returns Unit
        every { pinnedMessageRepository.findByConversationId(conversationId) } returns emptyList()

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

    // ─── #790: every member learns the new set of pins ─────────────────

    @Test
    fun `should broadcast the full set of pins to every member after a pin`() {
        memberOk()
        every { messageRepository.findById(messageId) } returns aMessage()
        every { pinnedMessageRepository.find(conversationId, messageId) } returns null
        every { pinnedMessageRepository.countByConversationId(conversationId) } returns 1
        every { pinnedMessageRepository.save(any()) } answers { firstArg() }
        val earlier = PinnedMessage(conversationId, UUID.randomUUID(), otherMember)
        val mine = PinnedMessage(conversationId, messageId, userId)
        every { pinnedMessageRepository.findByConversationId(conversationId) } returns listOf(earlier, mine)
        val recipients = slot<List<UUID>>()
        val frame = slot<WsMessage>()
        every { messageBroadcaster.broadcastToUsers(capture(recipients), capture(frame)) } returns Unit

        service.pin(conversationId, messageId, userId)

        assertEquals(setOf(userId, otherMember), recipients.captured.toSet())
        val pinned = frame.captured as WsMessage.PinnedMessagesChanged
        assertEquals(conversationId.toString(), pinned.conversationId)
        assertEquals(userId.toString(), pinned.changedBy)
        assertEquals(listOf(earlier.messageId, messageId).map { it.toString() }, pinned.pins.map { it.messageId })
    }

    @Test
    fun `should broadcast the remaining pins after an unpin`() {
        memberOk()
        every { pinnedMessageRepository.find(conversationId, messageId) } returns
            PinnedMessage(conversationId, messageId, userId)
        every { pinnedMessageRepository.delete(conversationId, messageId) } returns Unit
        every { pinnedMessageRepository.findByConversationId(conversationId) } returns emptyList()
        val frame = slot<WsMessage>()
        every { messageBroadcaster.broadcastToUsers(any(), capture(frame)) } returns Unit

        service.unpin(conversationId, messageId, userId)

        assertEquals(emptyList<Any>(), (frame.captured as WsMessage.PinnedMessagesChanged).pins)
    }

    @Test
    fun `should tell nobody anything when a re-pin or a stray unpin changed nothing`() {
        memberOk()
        every { messageRepository.findById(messageId) } returns aMessage()
        every { pinnedMessageRepository.find(conversationId, messageId) } returns
            PinnedMessage(conversationId, messageId, userId) andThen null
        every { pinnedMessageRepository.delete(conversationId, messageId) } returns Unit

        service.pin(conversationId, messageId, userId)
        service.unpin(conversationId, messageId, userId)

        verify(exactly = 0) { messageBroadcaster.broadcastToUsers(any(), any()) }
    }

    @Test
    fun `should not broadcast when the pin limit refuses the pin`() {
        memberOk()
        every { messageRepository.findById(messageId) } returns aMessage()
        every { pinnedMessageRepository.find(conversationId, messageId) } returns null
        every { pinnedMessageRepository.countByConversationId(conversationId) } returns 3

        assertThrows<BusinessException> { service.pin(conversationId, messageId, userId) }

        verify(exactly = 0) { messageBroadcaster.broadcastToUsers(any(), any()) }
    }
}
