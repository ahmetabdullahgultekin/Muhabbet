package com.muhabbet.messaging.domain.service

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * `GetConversationUseCase` — read one conversation by id, member-gated (#800).
 */
class GetConversationByIdTest {

    private lateinit var conversationRepository: ConversationRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var service: ConversationService

    private val me = TestData.USER_ID_1
    private val other = TestData.USER_ID_2
    private val stranger = TestData.USER_ID_3
    private val groupId = TestData.GROUP_ID

    @BeforeEach
    fun setUp() {
        conversationRepository = mockk()
        messageRepository = mockk()
        service = ConversationService(conversationRepository, messageRepository, mockk<UserRepository>())

        every { conversationRepository.findById(groupId) } returns
            TestData.groupConversation().copy(announcementOnly = true, disappearAfterSeconds = 3600)
        every { conversationRepository.findMembersByConversationId(groupId) } returns listOf(
            TestData.owner(conversationId = groupId, userId = me),
            TestData.member(conversationId = groupId, userId = other)
        )
        every { messageRepository.getLastMessages(listOf(groupId)) } returns
            mapOf(groupId to TestData.textMessage(conversationId = groupId))
        every { messageRepository.getUnreadCounts(listOf(groupId), me) } returns mapOf(groupId to 4)
    }

    @Test
    fun `should return members with their real roles and the conversation flags when the caller is a member`() {
        val detail = service.getConversation(me, groupId)

        assertEquals(true, detail.conversation.announcementOnly)
        assertEquals(3600, detail.conversation.disappearAfterSeconds)
        assertEquals(MemberRole.OWNER, detail.members.single { it.userId == me }.role)
        assertEquals(me, detail.viewer.userId)
        assertEquals(4, detail.unreadCount)
        assertEquals(groupId, detail.lastMessage?.conversationId)
    }

    @Test
    fun `should answer CONV_NOT_FOUND when the caller is not a member`() {
        val ex = assertThrows<BusinessException> { service.getConversation(stranger, groupId) }

        assertEquals(ErrorCode.CONV_NOT_FOUND, ex.errorCode)
        // Nothing about the conversation's content is read for a non-member.
        verify(exactly = 0) { messageRepository.getLastMessages(any()) }
        verify(exactly = 0) { messageRepository.getUnreadCounts(any(), any()) }
    }

    @Test
    fun `should answer CONV_NOT_FOUND when the conversation does not exist`() {
        val missing = java.util.UUID.randomUUID()
        every { conversationRepository.findById(missing) } returns null

        val ex = assertThrows<BusinessException> { service.getConversation(me, missing) }

        assertEquals(ErrorCode.CONV_NOT_FOUND, ex.errorCode)
    }
}
