package com.muhabbet.messaging.domain.service

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.`in`.SendMessageCommand
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.UUID

class MessagingServiceTest {

    private lateinit var conversationRepository: ConversationRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var userRepository: UserRepository
    private lateinit var messageBroadcaster: MessageBroadcaster
    private lateinit var eventPublisher: ApplicationEventPublisher
    private lateinit var messagingService: MessagingService

    private val userA = UUID.randomUUID()
    private val userB = UUID.randomUUID()
    private val userC = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        conversationRepository = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        userRepository = mockk()
        messageBroadcaster = mockk(relaxed = true)
        eventPublisher = mockk(relaxed = true)

        messagingService = MessagingService(
            conversationRepository = conversationRepository,
            messageRepository = messageRepository,
            userRepository = userRepository,
            messageBroadcaster = messageBroadcaster,
            eventPublisher = eventPublisher
        )
    }

    private fun stubUserExists(vararg ids: UUID) {
        ids.forEach { id ->
            every { userRepository.findById(id) } returns User(
                id = id, phoneNumber = "+905321234567",
                status = UserStatus.ACTIVE
            )
        }
    }

    // ─── createConversation ──────────────────────────────

    @Test
    fun `should create direct conversation when valid participants`() {
        stubUserExists(userA, userB)
        val convSlot = slot<Conversation>()
        every { conversationRepository.save(capture(convSlot)) } answers { convSlot.captured }
        every { conversationRepository.findDirectConversation(any(), any()) } returns null
        every { conversationRepository.saveMember(any()) } answers { firstArg() }

        val result = messagingService.createConversation(
            type = ConversationType.DIRECT,
            creatorId = userA,
            participantIds = listOf(userB)
        )

        assertEquals(ConversationType.DIRECT, result.conversation.type)
        assertEquals(2, result.members.size)
        verify { conversationRepository.saveDirectLookup(any(), any(), any()) }
    }

    @Test
    fun `should return existing conversation when direct conversation already exists`() {
        stubUserExists(userA, userB)
        val existingConvId = UUID.randomUUID()
        val existingConv = Conversation(id = existingConvId, type = ConversationType.DIRECT)
        val members = listOf(
            ConversationMember(conversationId = existingConvId, userId = userA),
            ConversationMember(conversationId = existingConvId, userId = userB)
        )

        every { conversationRepository.findDirectConversation(any(), any()) } returns existingConvId
        every { conversationRepository.findById(existingConvId) } returns existingConv
        every { conversationRepository.findMembersByConversationId(existingConvId) } returns members

        val result = messagingService.createConversation(
            type = ConversationType.DIRECT,
            creatorId = userA,
            participantIds = listOf(userB)
        )

        assertEquals(existingConvId, result.conversation.id)
        assertEquals(2, result.members.size)
        verify(exactly = 0) { conversationRepository.save(any()) }
    }

    @Test
    fun `should throw CONV_INVALID_PARTICIPANTS when direct with wrong participant count`() {
        stubUserExists(userA, userB, userC)

        val ex = assertThrows<BusinessException> {
            messagingService.createConversation(
                type = ConversationType.DIRECT,
                creatorId = userA,
                participantIds = listOf(userB, userC)
            )
        }
        assertEquals(ErrorCode.CONV_INVALID_PARTICIPANTS, ex.errorCode)
    }

    @Test
    fun `should throw CONV_INVALID_PARTICIPANTS when participant does not exist`() {
        every { userRepository.findById(userA) } returns User(
            id = userA, phoneNumber = "+905321234567", status = UserStatus.ACTIVE
        )
        every { userRepository.findById(userB) } returns null

        val ex = assertThrows<BusinessException> {
            messagingService.createConversation(
                type = ConversationType.DIRECT,
                creatorId = userA,
                participantIds = listOf(userB)
            )
        }
        assertEquals(ErrorCode.CONV_INVALID_PARTICIPANTS, ex.errorCode)
    }

    @Test
    fun `should create group conversation with owner role for creator`() {
        stubUserExists(userA, userB, userC)
        val convSlot = slot<Conversation>()
        every { conversationRepository.save(capture(convSlot)) } answers { convSlot.captured }
        val memberSlot = mutableListOf<ConversationMember>()
        every { conversationRepository.saveMember(capture(memberSlot)) } answers { firstArg() }

        val result = messagingService.createConversation(
            type = ConversationType.GROUP,
            creatorId = userA,
            participantIds = listOf(userB, userC),
            name = "Test Group"
        )

        assertEquals(ConversationType.GROUP, result.conversation.type)
        assertEquals(3, result.members.size)
        val creatorMember = memberSlot.find { it.userId == userA }
        assertNotNull(creatorMember)
        assertEquals(MemberRole.OWNER, creatorMember!!.role)
    }

    @Test
    fun `should throw VALIDATION_ERROR when group has no name`() {
        stubUserExists(userA, userB, userC)

        val ex = assertThrows<BusinessException> {
            messagingService.createConversation(
                type = ConversationType.GROUP,
                creatorId = userA,
                participantIds = listOf(userB, userC),
                name = null
            )
        }
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode)
    }

    // ─── sendMessage ─────────────────────────────────────

    @Test
    fun `should send message when sender is member`() {
        val convId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        val member = ConversationMember(conversationId = convId, userId = userA)
        val members = listOf(
            member,
            ConversationMember(conversationId = convId, userId = userB)
        )

        every { conversationRepository.findMember(convId, userA) } returns member
        every { messageRepository.existsById(messageId) } returns false
        every { messageRepository.save(any()) } answers { firstArg() }
        every { conversationRepository.findMembersByConversationId(convId) } returns members

        val result = messagingService.sendMessage(
            SendMessageCommand(
                messageId = messageId,
                conversationId = convId,
                senderId = userA,
                content = "Hello!",
                clientTimestamp = Instant.now()
            )
        )

        assertEquals(messageId, result.id)
        assertEquals("Hello!", result.content)
        verify { messageRepository.save(any()) }
        verify { messageBroadcaster.broadcastMessage(any(), listOf(userB)) }
    }

    @Test
    fun `should throw MSG_NOT_MEMBER when sender is not in conversation`() {
        val convId = UUID.randomUUID()
        every { conversationRepository.findMember(convId, userA) } returns null

        val ex = assertThrows<BusinessException> {
            messagingService.sendMessage(
                SendMessageCommand(
                    messageId = UUID.randomUUID(),
                    conversationId = convId,
                    senderId = userA,
                    content = "Hello!",
                    clientTimestamp = Instant.now()
                )
            )
        }
        assertEquals(ErrorCode.MSG_NOT_MEMBER, ex.errorCode)
    }

    @Test
    fun `should throw MSG_DUPLICATE when message already exists`() {
        val convId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        val member = ConversationMember(conversationId = convId, userId = userA)

        every { conversationRepository.findMember(convId, userA) } returns member
        every { messageRepository.existsById(messageId) } returns true

        val ex = assertThrows<BusinessException> {
            messagingService.sendMessage(
                SendMessageCommand(
                    messageId = messageId,
                    conversationId = convId,
                    senderId = userA,
                    content = "Hello!",
                    clientTimestamp = Instant.now()
                )
            )
        }
        assertEquals(ErrorCode.MSG_DUPLICATE, ex.errorCode)
    }

    @Test
    fun `should throw MSG_EMPTY_CONTENT when text message is blank`() {
        val convId = UUID.randomUUID()

        val ex = assertThrows<BusinessException> {
            messagingService.sendMessage(
                SendMessageCommand(
                    messageId = UUID.randomUUID(),
                    conversationId = convId,
                    senderId = userA,
                    content = "   ",
                    clientTimestamp = Instant.now()
                )
            )
        }
        assertEquals(ErrorCode.MSG_EMPTY_CONTENT, ex.errorCode)
    }

    @Test
    fun `should create delivery status for all recipients except sender`() {
        val convId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        val member = ConversationMember(conversationId = convId, userId = userA)
        val members = listOf(
            member,
            ConversationMember(conversationId = convId, userId = userB),
            ConversationMember(conversationId = convId, userId = userC)
        )

        every { conversationRepository.findMember(convId, userA) } returns member
        every { messageRepository.existsById(messageId) } returns false
        every { messageRepository.save(any()) } answers { firstArg() }
        every { conversationRepository.findMembersByConversationId(convId) } returns members

        messagingService.sendMessage(
            SendMessageCommand(
                messageId = messageId,
                conversationId = convId,
                senderId = userA,
                content = "Hello group!",
                clientTimestamp = Instant.now()
            )
        )

        verify(exactly = 2) { messageRepository.saveDeliveryStatus(any()) }
        verify { messageBroadcaster.broadcastMessage(any(), listOf(userB, userC)) }
    }

    // ─── getMessages ─────────────────────────────────────

    @Test
    fun `should return message page when user is member`() {
        val convId = UUID.randomUUID()
        val member = ConversationMember(conversationId = convId, userId = userA)
        val messages = listOf(
            Message(
                id = UUID.randomUUID(), conversationId = convId, senderId = userB,
                content = "Hi", clientTimestamp = Instant.now()
            )
        )

        every { conversationRepository.findMember(convId, userA) } returns member
        every { messageRepository.findByConversationId(convId, null, 51) } returns messages

        val page = messagingService.getMessages(convId, userA, null, 50, "before")

        assertEquals(1, page.items.size)
        assertFalse(page.hasMore)
    }

    @Test
    fun `should throw MSG_NOT_MEMBER when getting messages from non-member`() {
        val convId = UUID.randomUUID()
        every { conversationRepository.findMember(convId, userA) } returns null

        val ex = assertThrows<BusinessException> {
            messagingService.getMessages(convId, userA, null, 50, "before")
        }
        assertEquals(ErrorCode.MSG_NOT_MEMBER, ex.errorCode)
    }

    @Test
    fun `should paginate messages correctly when has more`() {
        val convId = UUID.randomUUID()
        val member = ConversationMember(conversationId = convId, userId = userA)
        val now = Instant.now()
        // Return limit + 1 messages to indicate hasMore
        val messages = (0..2).map { i ->
            Message(
                id = UUID.randomUUID(), conversationId = convId, senderId = userB,
                content = "msg $i", serverTimestamp = now.minusSeconds(i.toLong()),
                clientTimestamp = now
            )
        }

        every { conversationRepository.findMember(convId, userA) } returns member
        every { messageRepository.findByConversationId(convId, null, 3) } returns messages

        val page = messagingService.getMessages(convId, userA, null, 2, "before")

        assertEquals(2, page.items.size)
        assertTrue(page.hasMore)
        assertNotNull(page.nextCursor)
    }

    // ─── updateStatus ────────────────────────────────────

    @Test
    fun `should update delivery status and broadcast`() {
        val messageId = UUID.randomUUID()
        val convId = UUID.randomUUID()
        val message = Message(
            id = messageId, conversationId = convId, senderId = userA,
            content = "test", clientTimestamp = Instant.now()
        )

        every { messageRepository.findById(messageId) } returns message

        messagingService.updateStatus(messageId, userB, DeliveryStatus.DELIVERED)

        verify { messageRepository.updateDeliveryStatus(messageId, userB, DeliveryStatus.DELIVERED) }
        verify {
            messageBroadcaster.broadcastStatusUpdate(
                messageId, convId, userB, DeliveryStatus.DELIVERED
            )
        }
    }

    // ─── getConversations ────────────────────────────────

    @Test
    fun `should return conversation summaries for user`() {
        val convId = UUID.randomUUID()
        val conv = Conversation(id = convId, type = ConversationType.DIRECT)
        val lastMsg = Message(
            id = UUID.randomUUID(), conversationId = convId, senderId = userB,
            content = "Last message", serverTimestamp = Instant.now(),
            clientTimestamp = Instant.now()
        )

        every { conversationRepository.findConversationsByUserId(userA) } returns listOf(conv)
        every { messageRepository.getLastMessage(convId) } returns lastMsg
        every { messageRepository.getUnreadCount(convId, userA) } returns 3
        every { conversationRepository.findMembersByConversationId(convId) } returns listOf(
            ConversationMember(conversationId = convId, userId = userA),
            ConversationMember(conversationId = convId, userId = userB)
        )

        val page = messagingService.getConversations(userA, null, 20)

        assertEquals(1, page.items.size)
        assertEquals("Last message", page.items[0].lastMessagePreview)
        assertEquals(3, page.items[0].unreadCount)
    }
}
