package com.muhabbet.messaging.domain.service

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class ConversationServiceTest {

    private lateinit var conversationRepository: ConversationRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var userRepository: UserRepository
    private lateinit var messageBroadcaster: MessageBroadcaster
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

        conversationService = ConversationService(
            conversationRepository = conversationRepository,
            messageRepository = messageRepository,
            userRepository = userRepository
        )

        messageService = MessageService(
            conversationRepository = conversationRepository,
            messageRepository = messageRepository,
            messageBroadcaster = messageBroadcaster
        )
    }

    private fun stubUserExists(vararg ids: UUID) {
        val users = ids.map { id ->
            User(id = id, phoneNumber = "+905321234567", status = UserStatus.ACTIVE)
        }
        every { userRepository.findAllByIds(any()) } returns users
    }

    // ─── createConversation (additional edge cases) ──────────

    @Nested
    inner class CreateConversation {

        @Test
        fun `should create group with max members at limit`() {
            val participantIds = (0 until 255).map { UUID.randomUUID() } // 255 + creator = 256 = MAX
            val allIds = (participantIds + userA).distinct()

            val users = allIds.map { id ->
                User(id = id, phoneNumber = "+905321234567", status = UserStatus.ACTIVE)
            }
            every { userRepository.findAllByIds(any()) } returns users
            every { conversationRepository.save(any()) } answers { firstArg() }
            every { conversationRepository.saveMember(any()) } answers { firstArg() }

            val result = conversationService.createConversation(
                type = ConversationType.GROUP,
                creatorId = userA,
                participantIds = participantIds,
                name = "Max Group"
            )

            assertEquals(ConversationType.GROUP, result.conversation.type)
            assertEquals(256, result.members.size)
        }

        @Test
        fun `should throw CONV_MAX_MEMBERS when group exceeds max members`() {
            val participantIds = (0 until 256).map { UUID.randomUUID() } // 256 + creator = 257 > MAX
            val allIds = (participantIds + userA).distinct()

            val users = allIds.map { id ->
                User(id = id, phoneNumber = "+905321234567", status = UserStatus.ACTIVE)
            }
            every { userRepository.findAllByIds(any()) } returns users

            val ex = assertThrows<BusinessException> {
                conversationService.createConversation(
                    type = ConversationType.GROUP,
                    creatorId = userA,
                    participantIds = participantIds,
                    name = "Too Big Group"
                )
            }
            assertEquals(ErrorCode.CONV_MAX_MEMBERS, ex.errorCode)
        }

        @Test
        fun `should deduplicate creator from participant list`() {
            stubUserExists(userA, userB)
            every { conversationRepository.save(any()) } answers { firstArg() }
            every { conversationRepository.saveMember(any()) } answers { firstArg() }

            // Creator also in participant list
            val result = conversationService.createConversation(
                type = ConversationType.GROUP,
                creatorId = userA,
                participantIds = listOf(userA, userB),
                name = "Dedup Test"
            )

            // Should have 2 members, not 3
            assertEquals(2, result.members.size)
        }

        @Test
        fun `should throw VALIDATION_ERROR when group name is blank`() {
            stubUserExists(userA, userB)

            val ex = assertThrows<BusinessException> {
                conversationService.createConversation(
                    type = ConversationType.GROUP,
                    creatorId = userA,
                    participantIds = listOf(userB),
                    name = "   "
                )
            }
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode)
        }

        @Test
        fun `should throw VALIDATION_ERROR when group name exceeds max length`() {
            stubUserExists(userA, userB)

            val longName = "A".repeat(129)

            val ex = assertThrows<BusinessException> {
                conversationService.createConversation(
                    type = ConversationType.GROUP,
                    creatorId = userA,
                    participantIds = listOf(userB),
                    name = longName
                )
            }
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode)
        }

        @Test
        fun `should accept group name at exactly max length`() {
            stubUserExists(userA, userB)
            every { conversationRepository.save(any()) } answers { firstArg() }
            every { conversationRepository.saveMember(any()) } answers { firstArg() }

            val maxName = "A".repeat(128)

            val result = conversationService.createConversation(
                type = ConversationType.GROUP,
                creatorId = userA,
                participantIds = listOf(userB),
                name = maxName
            )

            assertEquals(maxName, result.conversation.name)
        }

        @Test
        fun `should not create direct conversation when self-messaging`() {
            val ex = assertThrows<BusinessException> {
                conversationService.createConversation(
                    type = ConversationType.DIRECT,
                    creatorId = userA,
                    participantIds = listOf(userA)
                )
            }
            assertEquals(ErrorCode.CONV_INVALID_PARTICIPANTS, ex.errorCode)
        }

        @Test
        fun `should throw CONV_INVALID_PARTICIPANTS when participant does not exist`() {
            // Mock findAllByIds to return only userA (not userB) => size mismatch
            val users = listOf(User(id = userA, phoneNumber = "+905321234567", status = UserStatus.ACTIVE))
            every { userRepository.findAllByIds(any()) } returns users

            val ex = assertThrows<BusinessException> {
                conversationService.createConversation(
                    type = ConversationType.DIRECT,
                    creatorId = userA,
                    participantIds = listOf(userB)
                )
            }
            assertEquals(ErrorCode.CONV_INVALID_PARTICIPANTS, ex.errorCode)
        }
    }

    // ─── getConversations ──────────────────────────────────

    @Nested
    inner class GetConversations {

        @Test
        fun `should return empty list when user has no conversations`() {
            every { conversationRepository.findConversationsByUserId(userA) } returns emptyList()

            val page = conversationService.getConversations(userA, null, 20)

            assertEquals(0, page.items.size)
            assertFalse(page.hasMore)
            assertNull(page.nextCursor)
        }

        @Test
        fun `should return conversations sorted by last message time`() {
            val conv1Id = UUID.randomUUID()
            val conv2Id = UUID.randomUUID()
            val now = Instant.now()

            val conv1 = Conversation(id = conv1Id, type = ConversationType.DIRECT)
            val conv2 = Conversation(id = conv2Id, type = ConversationType.GROUP, name = "Group")

            every { conversationRepository.findConversationsByUserId(userA) } returns listOf(conv1, conv2)

            // Batch: conv2 has more recent message
            every { messageRepository.getLastMessages(any()) } returns mapOf(
                conv1Id to Message(
                    id = UUID.randomUUID(), conversationId = conv1Id, senderId = userB,
                    content = "Older msg", serverTimestamp = now.minusSeconds(3600),
                    clientTimestamp = now
                ),
                conv2Id to Message(
                    id = UUID.randomUUID(), conversationId = conv2Id, senderId = userC,
                    content = "Newer msg", serverTimestamp = now.minusSeconds(60),
                    clientTimestamp = now
                )
            )
            every { messageRepository.getUnreadCounts(any(), eq(userA)) } returns mapOf(
                conv1Id to 0,
                conv2Id to 0
            )
            every { conversationRepository.findMembersByConversationIds(any()) } returns mapOf(
                conv1Id to listOf(ConversationMember(conversationId = conv1Id, userId = userA)),
                conv2Id to listOf(ConversationMember(conversationId = conv2Id, userId = userA))
            )

            val page = conversationService.getConversations(userA, null, 20)

            assertEquals(2, page.items.size)
            assertEquals("Newer msg", page.items[0].lastMessagePreview)
            assertEquals("Older msg", page.items[1].lastMessagePreview)
        }

        @Test
        fun `should return correct unread counts per conversation`() {
            val conv1Id = UUID.randomUUID()
            val now = Instant.now()
            val conv1 = Conversation(id = conv1Id, type = ConversationType.DIRECT)

            every { conversationRepository.findConversationsByUserId(userA) } returns listOf(conv1)
            every { messageRepository.getLastMessages(any()) } returns mapOf(
                conv1Id to Message(
                    id = UUID.randomUUID(), conversationId = conv1Id, senderId = userB,
                    content = "Hello", serverTimestamp = now, clientTimestamp = now
                )
            )
            every { messageRepository.getUnreadCounts(any(), eq(userA)) } returns mapOf(
                conv1Id to 7
            )
            every { conversationRepository.findMembersByConversationIds(any()) } returns mapOf(
                conv1Id to listOf(
                    ConversationMember(conversationId = conv1Id, userId = userA),
                    ConversationMember(conversationId = conv1Id, userId = userB)
                )
            )

            val page = conversationService.getConversations(userA, null, 20)

            assertEquals(7, page.items[0].unreadCount)
        }

        @Test
        fun `should paginate conversations when has more than limit`() {
            val now = Instant.now()
            val conversations = (0 until 5).map {
                Conversation(id = UUID.randomUUID(), type = ConversationType.DIRECT)
            }

            every { conversationRepository.findConversationsByUserId(userA) } returns conversations

            val lastMessages = conversations.mapIndexed { i, conv ->
                conv.id to Message(
                    id = UUID.randomUUID(), conversationId = conv.id, senderId = userB,
                    content = "msg $i", serverTimestamp = now.minusSeconds(i.toLong()),
                    clientTimestamp = now
                )
            }.toMap()
            every { messageRepository.getLastMessages(any()) } returns lastMessages

            val unreadCounts = conversations.associate { it.id to 0 }
            every { messageRepository.getUnreadCounts(any(), eq(userA)) } returns unreadCounts

            val membersMap = conversations.associate { conv ->
                conv.id to listOf(ConversationMember(conversationId = conv.id, userId = userA))
            }
            every { conversationRepository.findMembersByConversationIds(any()) } returns membersMap

            val page = conversationService.getConversations(userA, null, 3)

            assertEquals(3, page.items.size)
            assertTrue(page.hasMore)
            assertNotNull(page.nextCursor)
        }

        @Test
        fun `should return last page without next cursor`() {
            val now = Instant.now()
            val conversations = (0 until 2).map {
                Conversation(id = UUID.randomUUID(), type = ConversationType.DIRECT)
            }

            every { conversationRepository.findConversationsByUserId(userA) } returns conversations

            val lastMessages = conversations.mapIndexed { i, conv ->
                conv.id to Message(
                    id = UUID.randomUUID(), conversationId = conv.id, senderId = userB,
                    content = "msg $i", serverTimestamp = now.minusSeconds(i.toLong()),
                    clientTimestamp = now
                )
            }.toMap()
            every { messageRepository.getLastMessages(any()) } returns lastMessages

            val unreadCounts = conversations.associate { it.id to 0 }
            every { messageRepository.getUnreadCounts(any(), eq(userA)) } returns unreadCounts

            val membersMap = conversations.associate { conv ->
                conv.id to listOf(ConversationMember(conversationId = conv.id, userId = userA))
            }
            every { conversationRepository.findMembersByConversationIds(any()) } returns membersMap

            val page = conversationService.getConversations(userA, null, 20)

            assertEquals(2, page.items.size)
            assertFalse(page.hasMore)
            assertNull(page.nextCursor)
        }

        @Test
        fun `should truncate last message preview to 100 characters`() {
            val convId = UUID.randomUUID()
            val now = Instant.now()
            val conv = Conversation(id = convId, type = ConversationType.DIRECT)
            val longMessage = "A".repeat(200)

            every { conversationRepository.findConversationsByUserId(userA) } returns listOf(conv)
            every { messageRepository.getLastMessages(any()) } returns mapOf(
                convId to Message(
                    id = UUID.randomUUID(), conversationId = convId, senderId = userB,
                    content = longMessage, serverTimestamp = now, clientTimestamp = now
                )
            )
            every { messageRepository.getUnreadCounts(any(), eq(userA)) } returns mapOf(convId to 0)
            every { conversationRepository.findMembersByConversationIds(any()) } returns mapOf(
                convId to listOf(ConversationMember(conversationId = convId, userId = userA))
            )

            val page = conversationService.getConversations(userA, null, 20)

            assertEquals(100, page.items[0].lastMessagePreview?.length)
        }

        @Test
        fun `should handle conversation with no messages`() {
            val convId = UUID.randomUUID()
            val conv = Conversation(id = convId, type = ConversationType.DIRECT)

            every { conversationRepository.findConversationsByUserId(userA) } returns listOf(conv)
            every { messageRepository.getLastMessages(any()) } returns emptyMap()
            every { messageRepository.getUnreadCounts(any(), eq(userA)) } returns mapOf(convId to 0)
            every { conversationRepository.findMembersByConversationIds(any()) } returns mapOf(
                convId to listOf(
                    ConversationMember(conversationId = convId, userId = userA),
                    ConversationMember(conversationId = convId, userId = userB)
                )
            )

            val page = conversationService.getConversations(userA, null, 20)

            assertEquals(1, page.items.size)
            assertNull(page.items[0].lastMessagePreview)
            assertNull(page.items[0].lastMessageAt)
        }

        @Test
        fun `should coerce limit to valid range`() {
            every { conversationRepository.findConversationsByUserId(userA) } returns emptyList()

            // Limit of 0 should be coerced to 1, limit of 100 to 50
            val page1 = conversationService.getConversations(userA, null, 0)
            val page2 = conversationService.getConversations(userA, null, 100)

            // Both should work without error
            assertEquals(0, page1.items.size)
            assertEquals(0, page2.items.size)
        }

        @Test
        fun `should include participant IDs in summary`() {
            val convId = UUID.randomUUID()
            val now = Instant.now()
            val conv = Conversation(id = convId, type = ConversationType.DIRECT)

            every { conversationRepository.findConversationsByUserId(userA) } returns listOf(conv)
            every { messageRepository.getLastMessages(any()) } returns mapOf(
                convId to Message(
                    id = UUID.randomUUID(), conversationId = convId, senderId = userB,
                    content = "Hi", serverTimestamp = now, clientTimestamp = now
                )
            )
            every { messageRepository.getUnreadCounts(any(), eq(userA)) } returns mapOf(convId to 0)
            every { conversationRepository.findMembersByConversationIds(any()) } returns mapOf(
                convId to listOf(
                    ConversationMember(conversationId = convId, userId = userA),
                    ConversationMember(conversationId = convId, userId = userB)
                )
            )

            val page = conversationService.getConversations(userA, null, 20)

            assertEquals(2, page.items[0].participantIds.size)
            assertTrue(page.items[0].participantIds.contains(userA))
            assertTrue(page.items[0].participantIds.contains(userB))
        }

        @Test
        fun `should return type as lowercase string`() {
            val convId = UUID.randomUUID()
            val now = Instant.now()
            val conv = Conversation(id = convId, type = ConversationType.DIRECT)

            every { conversationRepository.findConversationsByUserId(userA) } returns listOf(conv)
            every { messageRepository.getLastMessages(any()) } returns mapOf(
                convId to Message(
                    id = UUID.randomUUID(), conversationId = convId, senderId = userB,
                    content = "Hi", serverTimestamp = now, clientTimestamp = now
                )
            )
            every { messageRepository.getUnreadCounts(any(), eq(userA)) } returns mapOf(convId to 0)
            every { conversationRepository.findMembersByConversationIds(any()) } returns mapOf(
                convId to listOf(ConversationMember(conversationId = convId, userId = userA))
            )

            val page = conversationService.getConversations(userA, null, 20)

            assertEquals("direct", page.items[0].type)
        }
    }

    // ─── deleteMessage ──────────────────────────────────────

    @Nested
    inner class DeleteMessage {

        @Test
        fun `should soft delete message when requester is sender`() {
            val messageId = UUID.randomUUID()
            val convId = UUID.randomUUID()
            val message = Message(
                id = messageId, conversationId = convId, senderId = userA,
                content = "to delete", clientTimestamp = Instant.now()
            )

            every { messageRepository.findById(messageId) } returns message
            every { conversationRepository.findMembersByConversationId(convId) } returns listOf(
                ConversationMember(conversationId = convId, userId = userA),
                ConversationMember(conversationId = convId, userId = userB)
            )

            messageService.deleteMessage(messageId, userA)

            verify { messageRepository.softDelete(messageId) }
            verify { messageBroadcaster.broadcastToUsers(any(), any()) }
        }

        @Test
        fun `should throw MSG_NOT_FOUND when message does not exist`() {
            val messageId = UUID.randomUUID()

            every { messageRepository.findById(messageId) } returns null

            val ex = assertThrows<BusinessException> {
                messageService.deleteMessage(messageId, userA)
            }
            assertEquals(ErrorCode.MSG_NOT_FOUND, ex.errorCode)
        }

        @Test
        fun `should throw MSG_NOT_SENDER when requester is not the sender`() {
            val messageId = UUID.randomUUID()
            val message = Message(
                id = messageId, conversationId = UUID.randomUUID(), senderId = userB,
                content = "not your message", clientTimestamp = Instant.now()
            )

            every { messageRepository.findById(messageId) } returns message

            val ex = assertThrows<BusinessException> {
                messageService.deleteMessage(messageId, userA)
            }
            assertEquals(ErrorCode.MSG_NOT_SENDER, ex.errorCode)
        }

        @Test
        fun `should throw MSG_ALREADY_DELETED when message is already deleted`() {
            val messageId = UUID.randomUUID()
            val message = Message(
                id = messageId, conversationId = UUID.randomUUID(), senderId = userA,
                content = "deleted", clientTimestamp = Instant.now(),
                isDeleted = true, deletedAt = Instant.now()
            )

            every { messageRepository.findById(messageId) } returns message

            val ex = assertThrows<BusinessException> {
                messageService.deleteMessage(messageId, userA)
            }
            assertEquals(ErrorCode.MSG_ALREADY_DELETED, ex.errorCode)
        }
    }

    // ─── editMessage ──────────────────────────────────────

    @Nested
    inner class EditMessage {

        @Test
        fun `should edit message successfully when within edit window`() {
            val messageId = UUID.randomUUID()
            val convId = UUID.randomUUID()
            val message = Message(
                id = messageId, conversationId = convId, senderId = userA,
                content = "original", clientTimestamp = Instant.now(),
                serverTimestamp = Instant.now().minusSeconds(60) // 1 minute ago
            )

            every { messageRepository.findById(messageId) } returns message
            every { conversationRepository.findMembersByConversationId(convId) } returns listOf(
                ConversationMember(conversationId = convId, userId = userA),
                ConversationMember(conversationId = convId, userId = userB)
            )

            val result = messageService.editMessage(messageId, userA, "edited content")

            assertEquals("edited content", result.content)
            assertNotNull(result.editedAt)
            verify { messageRepository.updateContent(eq(messageId), eq("edited content"), any()) }
            verify { messageBroadcaster.broadcastToUsers(any(), any()) }
        }

        @Test
        fun `should throw MSG_NOT_FOUND when message does not exist`() {
            val messageId = UUID.randomUUID()

            every { messageRepository.findById(messageId) } returns null

            val ex = assertThrows<BusinessException> {
                messageService.editMessage(messageId, userA, "new content")
            }
            assertEquals(ErrorCode.MSG_NOT_FOUND, ex.errorCode)
        }

        @Test
        fun `should throw MSG_NOT_SENDER when requester is not the sender`() {
            val messageId = UUID.randomUUID()
            val message = Message(
                id = messageId, conversationId = UUID.randomUUID(), senderId = userB,
                content = "original", clientTimestamp = Instant.now(),
                serverTimestamp = Instant.now()
            )

            every { messageRepository.findById(messageId) } returns message

            val ex = assertThrows<BusinessException> {
                messageService.editMessage(messageId, userA, "hacked content")
            }
            assertEquals(ErrorCode.MSG_NOT_SENDER, ex.errorCode)
        }

        @Test
        fun `should throw MSG_ALREADY_DELETED when message is deleted`() {
            val messageId = UUID.randomUUID()
            val message = Message(
                id = messageId, conversationId = UUID.randomUUID(), senderId = userA,
                content = "deleted", clientTimestamp = Instant.now(),
                serverTimestamp = Instant.now(),
                isDeleted = true
            )

            every { messageRepository.findById(messageId) } returns message

            val ex = assertThrows<BusinessException> {
                messageService.editMessage(messageId, userA, "new content")
            }
            assertEquals(ErrorCode.MSG_ALREADY_DELETED, ex.errorCode)
        }

        @Test
        fun `should throw MSG_EDIT_WINDOW_EXPIRED when past 15 minutes`() {
            val messageId = UUID.randomUUID()
            val message = Message(
                id = messageId, conversationId = UUID.randomUUID(), senderId = userA,
                content = "original", clientTimestamp = Instant.now(),
                serverTimestamp = Instant.now().minusSeconds(16 * 60) // 16 minutes ago
            )

            every { messageRepository.findById(messageId) } returns message

            val ex = assertThrows<BusinessException> {
                messageService.editMessage(messageId, userA, "too late")
            }
            assertEquals(ErrorCode.MSG_EDIT_WINDOW_EXPIRED, ex.errorCode)
        }

        @Test
        fun `should throw MSG_EMPTY_CONTENT when new content is blank`() {
            val messageId = UUID.randomUUID()
            val message = Message(
                id = messageId, conversationId = UUID.randomUUID(), senderId = userA,
                content = "original", clientTimestamp = Instant.now(),
                serverTimestamp = Instant.now().minusSeconds(60)
            )

            every { messageRepository.findById(messageId) } returns message

            val ex = assertThrows<BusinessException> {
                messageService.editMessage(messageId, userA, "   ")
            }
            assertEquals(ErrorCode.MSG_EMPTY_CONTENT, ex.errorCode)
        }

        @Test
        fun `should throw MSG_CONTENT_TOO_LONG when new content exceeds limit`() {
            val messageId = UUID.randomUUID()
            val message = Message(
                id = messageId, conversationId = UUID.randomUUID(), senderId = userA,
                content = "original", clientTimestamp = Instant.now(),
                serverTimestamp = Instant.now().minusSeconds(60)
            )

            every { messageRepository.findById(messageId) } returns message

            val longContent = "A".repeat(10_001)

            val ex = assertThrows<BusinessException> {
                messageService.editMessage(messageId, userA, longContent)
            }
            assertEquals(ErrorCode.MSG_CONTENT_TOO_LONG, ex.errorCode)
        }

        @Test
        fun `should edit message at exactly 15 minute boundary`() {
            val messageId = UUID.randomUUID()
            val convId = UUID.randomUUID()
            val message = Message(
                id = messageId, conversationId = convId, senderId = userA,
                content = "original", clientTimestamp = Instant.now(),
                serverTimestamp = Instant.now().minusSeconds(15 * 60) // Exactly 15 min
            )

            every { messageRepository.findById(messageId) } returns message
            every { conversationRepository.findMembersByConversationId(convId) } returns listOf(
                ConversationMember(conversationId = convId, userId = userA)
            )

            // At exactly 15 minutes, the Duration.toMinutes() returns 15 which is NOT > 15
            val result = messageService.editMessage(messageId, userA, "just in time")

            assertEquals("just in time", result.content)
        }
    }
}
