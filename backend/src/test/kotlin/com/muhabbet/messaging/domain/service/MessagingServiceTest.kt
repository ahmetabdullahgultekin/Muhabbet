package com.muhabbet.messaging.domain.service

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.model.Mention
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.MessageDeliveryStatus
import com.muhabbet.messaging.domain.port.`in`.SendMessageCommand
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MentionRepository
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
import java.time.Instant
import java.util.UUID

class MessagingServiceTest {

    private lateinit var conversationRepository: ConversationRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var userRepository: UserRepository
    private lateinit var messageBroadcaster: MessageBroadcaster
    private lateinit var mentionRepository: MentionRepository
    private lateinit var conversationService: ConversationService
    private lateinit var messageService: MessageService

    private val userA = UUID.randomUUID()
    private val userB = UUID.randomUUID()
    private val userC = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        conversationRepository = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        userRepository = mockk()
        messageBroadcaster = mockk(relaxed = true)
        mentionRepository = mockk(relaxed = true)

        conversationService = ConversationService(
            conversationRepository = conversationRepository,
            messageRepository = messageRepository,
            userRepository = userRepository
        )

        // Default service has the @mentions flag OFF — exercises behaviour-neutral path.
        messageService = MessageService(
            conversationRepository = conversationRepository,
            messageRepository = messageRepository,
            messageBroadcaster = messageBroadcaster,
            mentionRepository = mentionRepository,
            mentionsEnabled = false
        )
    }

    /** Builds a MessageService with the @mentions flag ON (S2 persist + validate path). */
    private fun mentionsEnabledService(): MessageService = MessageService(
        conversationRepository = conversationRepository,
        messageRepository = messageRepository,
        messageBroadcaster = messageBroadcaster,
        mentionRepository = mentionRepository,
        mentionsEnabled = true
    )

    private fun stubUserExists(vararg ids: UUID) {
        val users = ids.map { id -> User(id = id, phoneNumber = "+905321234567", status = UserStatus.ACTIVE) }
        every { userRepository.findAllByIds(any()) } returns users
    }

    // ─── createConversation ──────────────────────────────

    @Test
    fun `should create direct conversation when valid participants`() {
        stubUserExists(userA, userB)
        val convSlot = slot<Conversation>()
        every { conversationRepository.save(capture(convSlot)) } answers { convSlot.captured }
        every { conversationRepository.findDirectConversation(any(), any()) } returns null
        every { conversationRepository.saveMember(any()) } answers { firstArg() }

        val result = conversationService.createConversation(
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

        val result = conversationService.createConversation(
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
            conversationService.createConversation(
                type = ConversationType.DIRECT,
                creatorId = userA,
                participantIds = listOf(userB, userC)
            )
        }
        assertEquals(ErrorCode.CONV_INVALID_PARTICIPANTS, ex.errorCode)
    }

    @Test
    fun `should throw CONV_INVALID_PARTICIPANTS when participant does not exist`() {
        // Only return the creator — userB is missing from the result
        val creatorOnly = listOf(User(id = userA, phoneNumber = "+905321234567", status = UserStatus.ACTIVE))
        every { userRepository.findAllByIds(any()) } returns creatorOnly

        val ex = assertThrows<BusinessException> {
            conversationService.createConversation(
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

        val result = conversationService.createConversation(
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
            conversationService.createConversation(
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

        val result = messageService.sendMessage(
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
            messageService.sendMessage(
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
            messageService.sendMessage(
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
            messageService.sendMessage(
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

        messageService.sendMessage(
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

    // ─── @mentions (Tier 2, S2) ──────────────────────────

    @Test
    fun `should persist mention rows when flag is ON and mentioned user is a member`() {
        val convId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        val sender = ConversationMember(conversationId = convId, userId = userA)
        val members = listOf(
            sender,
            ConversationMember(conversationId = convId, userId = userB)
        )

        every { conversationRepository.findMember(convId, userA) } returns sender
        every { messageRepository.existsById(messageId) } returns false
        every { messageRepository.save(any()) } answers { firstArg() }
        every { conversationRepository.findMembersByConversationId(convId) } returns members
        val savedMentions = slot<List<Mention>>()
        every { mentionRepository.saveAll(eq(messageId), capture(savedMentions)) } returns Unit

        val result = mentionsEnabledService().sendMessage(
            SendMessageCommand(
                messageId = messageId,
                conversationId = convId,
                senderId = userA,
                content = "hey @b look",
                clientTimestamp = Instant.now(),
                mentions = listOf(Mention(mentionedUserId = userB, startOffset = 4, length = 2))
            )
        )

        verify(exactly = 1) { mentionRepository.saveAll(eq(messageId), any()) }
        assertEquals(1, savedMentions.captured.size)
        assertEquals(userB, savedMentions.captured.first().mentionedUserId)
        // Persisted mention is also reflected on the returned domain message.
        assertEquals(1, result.mentions.size)
        assertFalse(result.mentionsEveryone)
    }

    @Test
    fun `should drop mentions of non-members when flag is ON`() {
        val convId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        val sender = ConversationMember(conversationId = convId, userId = userA)
        val members = listOf(
            sender,
            ConversationMember(conversationId = convId, userId = userB)
        )

        every { conversationRepository.findMember(convId, userA) } returns sender
        every { messageRepository.existsById(messageId) } returns false
        every { messageRepository.save(any()) } answers { firstArg() }
        every { conversationRepository.findMembersByConversationId(convId) } returns members
        val savedMentions = slot<List<Mention>>()
        every { mentionRepository.saveAll(eq(messageId), capture(savedMentions)) } returns Unit

        mentionsEnabledService().sendMessage(
            SendMessageCommand(
                messageId = messageId,
                conversationId = convId,
                senderId = userA,
                content = "hey @b and @c",
                clientTimestamp = Instant.now(),
                // userB is a member, userC is NOT → userC must be dropped.
                mentions = listOf(
                    Mention(mentionedUserId = userB, startOffset = 4, length = 2),
                    Mention(mentionedUserId = userC, startOffset = 11, length = 2)
                )
            )
        )

        assertEquals(1, savedMentions.captured.size)
        assertEquals(userB, savedMentions.captured.first().mentionedUserId)
    }

    @Test
    fun `should throw MSG_MENTION_EVERYONE_FORBIDDEN when non-admin uses everyone`() {
        val convId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        // Sender is a plain MEMBER → not allowed to @everyone.
        val sender = ConversationMember(conversationId = convId, userId = userA, role = MemberRole.MEMBER)
        val members = listOf(sender, ConversationMember(conversationId = convId, userId = userB))

        every { conversationRepository.findMember(convId, userA) } returns sender
        every { messageRepository.existsById(messageId) } returns false
        every { conversationRepository.findMembersByConversationId(convId) } returns members

        val ex = assertThrows<BusinessException> {
            mentionsEnabledService().sendMessage(
                SendMessageCommand(
                    messageId = messageId,
                    conversationId = convId,
                    senderId = userA,
                    content = "@everyone meeting now",
                    clientTimestamp = Instant.now(),
                    mentionsEveryone = true
                )
            )
        }

        assertEquals(ErrorCode.MSG_MENTION_EVERYONE_FORBIDDEN, ex.errorCode)
        // No message should be saved when the @everyone gate rejects the send.
        verify(exactly = 0) { messageRepository.save(any()) }
        verify(exactly = 0) { mentionRepository.saveAll(any(), any()) }
    }

    @Test
    fun `should allow everyone when sender is admin`() {
        val convId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        val sender = ConversationMember(conversationId = convId, userId = userA, role = MemberRole.ADMIN)
        val members = listOf(sender, ConversationMember(conversationId = convId, userId = userB))

        every { conversationRepository.findMember(convId, userA) } returns sender
        every { messageRepository.existsById(messageId) } returns false
        every { messageRepository.save(any()) } answers { firstArg() }
        every { conversationRepository.findMembersByConversationId(convId) } returns members

        val result = mentionsEnabledService().sendMessage(
            SendMessageCommand(
                messageId = messageId,
                conversationId = convId,
                senderId = userA,
                content = "@everyone meeting now",
                clientTimestamp = Instant.now(),
                mentionsEveryone = true
            )
        )

        assertTrue(result.mentionsEveryone)
        verify { messageRepository.save(any()) }
    }

    @Test
    fun `should ignore mentions entirely when flag is OFF`() {
        val convId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        // Sender is a plain MEMBER and uses @everyone — would be forbidden if the flag were ON,
        // but with the flag OFF mentions must be ignored (no throw, nothing persisted).
        val sender = ConversationMember(conversationId = convId, userId = userA, role = MemberRole.MEMBER)
        val members = listOf(sender, ConversationMember(conversationId = convId, userId = userB))

        every { conversationRepository.findMember(convId, userA) } returns sender
        every { messageRepository.existsById(messageId) } returns false
        every { messageRepository.save(any()) } answers { firstArg() }
        every { conversationRepository.findMembersByConversationId(convId) } returns members

        // Uses the default (flag-OFF) service.
        val result = messageService.sendMessage(
            SendMessageCommand(
                messageId = messageId,
                conversationId = convId,
                senderId = userA,
                content = "@everyone @c hello",
                clientTimestamp = Instant.now(),
                mentions = listOf(Mention(mentionedUserId = userC, startOffset = 10, length = 2)),
                mentionsEveryone = true
            )
        )

        assertTrue(result.mentions.isEmpty())
        assertFalse(result.mentionsEveryone)
        // saveAll is still called, but always with an empty list when the flag is OFF.
        verify { mentionRepository.saveAll(messageId, emptyList()) }
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

        val page = messageService.getMessages(convId, userA, null, 50, "before")

        assertEquals(1, page.items.size)
        assertFalse(page.hasMore)
    }

    @Test
    fun `should throw MSG_NOT_MEMBER when getting messages from non-member`() {
        val convId = UUID.randomUUID()
        every { conversationRepository.findMember(convId, userA) } returns null

        val ex = assertThrows<BusinessException> {
            messageService.getMessages(convId, userA, null, 50, "before")
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

        val page = messageService.getMessages(convId, userA, null, 2, "before")

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
        every { conversationRepository.findMember(convId, userB) } returns
            ConversationMember(conversationId = convId, userId = userB)

        messageService.updateStatus(messageId, userB, DeliveryStatus.DELIVERED)

        verify { messageRepository.updateDeliveryStatus(messageId, userB, DeliveryStatus.DELIVERED) }
        verify {
            messageBroadcaster.broadcastStatusUpdate(
                messageId, convId, userB, userA, DeliveryStatus.DELIVERED
            )
        }
    }

    @Test
    fun `updateStatus should not write or broadcast when user is not a conversation member`() {
        // Read-receipt spoof guard: a non-member who knows a messageId must not be able to forge a
        // DELIVERED/READ StatusUpdate to the real sender.
        val messageId = UUID.randomUUID()
        val convId = UUID.randomUUID()
        val message = Message(
            id = messageId, conversationId = convId, senderId = userA,
            content = "test", clientTimestamp = Instant.now()
        )

        every { messageRepository.findById(messageId) } returns message
        every { conversationRepository.findMember(convId, userC) } returns null

        messageService.updateStatus(messageId, userC, DeliveryStatus.READ)

        verify(exactly = 0) { messageRepository.updateDeliveryStatus(any(), any(), any()) }
        verify(exactly = 0) { messageBroadcaster.broadcastStatusUpdate(any(), any(), any(), any(), any()) }
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
        every { messageRepository.getLastMessages(any()) } returns mapOf(convId to lastMsg)
        every { messageRepository.getUnreadCounts(any(), eq(userA)) } returns mapOf(convId to 3)
        every { conversationRepository.findMembersByConversationIds(any()) } returns mapOf(
            convId to listOf(
                ConversationMember(conversationId = convId, userId = userA),
                ConversationMember(conversationId = convId, userId = userB)
            )
        )

        val page = conversationService.getConversations(userA, null, 20)

        assertEquals(1, page.items.size)
        assertEquals("Last message", page.items[0].lastMessagePreview)
        assertEquals(3, page.items[0].unreadCount)
    }

    // ─── getMessageInfo IDOR guard ───────────────────────

    @Test
    fun `getMessageInfo should return info when requester is conversation member`() {
        val convId = UUID.randomUUID()
        val message = Message(
            id = UUID.randomUUID(),
            conversationId = convId,
            senderId = userA,
            content = "secret",
            clientTimestamp = Instant.now()
        )
        every { messageRepository.findById(message.id) } returns message
        every { conversationRepository.findMember(convId, userB) } returns
            ConversationMember(conversationId = convId, userId = userB)
        val statuses = listOf(
            MessageDeliveryStatus(messageId = message.id, userId = userB, status = DeliveryStatus.READ)
        )
        every { messageRepository.getDeliveryStatuses(listOf(message.id)) } returns statuses

        val info = messageService.getMessageInfo(message.id, userB)

        assertEquals(message.id, info.message.id)
        assertEquals(1, info.deliveryStatuses.size)
    }

    @Test
    fun `getMessageInfo should throw MSG_NOT_MEMBER when requester is not a member`() {
        val convId = UUID.randomUUID()
        val message = Message(
            id = UUID.randomUUID(),
            conversationId = convId,
            senderId = userA,
            content = "secret",
            clientTimestamp = Instant.now()
        )
        every { messageRepository.findById(message.id) } returns message
        // userC is NOT a member of the conversation
        every { conversationRepository.findMember(convId, userC) } returns null

        val ex = assertThrows<BusinessException> {
            messageService.getMessageInfo(message.id, userC)
        }
        assertEquals(ErrorCode.MSG_NOT_MEMBER, ex.errorCode)
        // Must not leak content/recipients to a non-member.
        verify(exactly = 0) { messageRepository.getDeliveryStatuses(any()) }
    }

    @Test
    fun `getMessageInfo should throw MSG_NOT_FOUND when message does not exist`() {
        val missingId = UUID.randomUUID()
        every { messageRepository.findById(missingId) } returns null

        val ex = assertThrows<BusinessException> {
            messageService.getMessageInfo(missingId, userA)
        }
        assertEquals(ErrorCode.MSG_NOT_FOUND, ex.errorCode)
    }

    // ─── markViewOnceViewed IDOR guard ───────────────────

    @Test
    fun `markViewOnceViewed should mark when requester is a member and not the sender`() {
        val convId = UUID.randomUUID()
        val message = Message(
            id = UUID.randomUUID(),
            conversationId = convId,
            senderId = userA,
            content = "peek",
            viewOnce = true,
            clientTimestamp = Instant.now()
        )
        every { messageRepository.findById(message.id) } returns message
        every { conversationRepository.findMember(convId, userB) } returns
            ConversationMember(conversationId = convId, userId = userB)

        messageService.markViewOnceViewed(message.id, userB)

        verify(exactly = 1) { messageRepository.markViewOnceViewed(message.id, userB) }
    }

    @Test
    fun `markViewOnceViewed should throw MSG_NOT_MEMBER and not burn the message for a non-member`() {
        val convId = UUID.randomUUID()
        val message = Message(
            id = UUID.randomUUID(),
            conversationId = convId,
            senderId = userA,
            content = "peek",
            viewOnce = true,
            clientTimestamp = Instant.now()
        )
        every { messageRepository.findById(message.id) } returns message
        // userC knows the messageId but is NOT a member
        every { conversationRepository.findMember(convId, userC) } returns null

        val ex = assertThrows<BusinessException> {
            messageService.markViewOnceViewed(message.id, userC)
        }
        assertEquals(ErrorCode.MSG_NOT_MEMBER, ex.errorCode)
        // The whole point of the IDOR: a non-member must NOT be able to burn the view-once.
        verify(exactly = 0) { messageRepository.markViewOnceViewed(any(), any()) }
    }
}
