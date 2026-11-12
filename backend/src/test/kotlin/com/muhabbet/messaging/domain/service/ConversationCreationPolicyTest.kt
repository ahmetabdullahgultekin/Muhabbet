package com.muhabbet.messaging.domain.service

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

/**
 * Creating a conversation must not be a way around a block or a way to enrol strangers in a
 * broadcast. A group cannot include someone who has blocked its creator — the same rule adding a
 * member already follows — and a channel is created empty, owned by its creator, and announcement
 * only: subscribing is the only way in.
 */
class ConversationCreationPolicyTest {

    private lateinit var conversations: ConversationRepository
    private lateinit var users: UserRepository
    private lateinit var blockPolicy: BlockPolicyPort
    private lateinit var service: ConversationService

    private val creator = UUID.randomUUID()
    private val friend = UUID.randomUUID()
    private val blocker = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        conversations = mockk(relaxed = true)
        every { conversations.save(any()) } answers { firstArg() }
        every { conversations.saveMember(any()) } answers { firstArg() }
        users = mockk()
        every { users.findAllByIds(any()) } answers {
            firstArg<List<UUID>>().map { User(id = it, phoneNumber = "+905000000009", status = UserStatus.ACTIVE) }
        }
        blockPolicy = mockk()
        every { blockPolicy.hasBlocked(any(), any()) } returns false
        every { blockPolicy.hasBlocked(blocker, creator) } returns true
        service = ConversationService(conversations, mockk(relaxed = true), users, blockPolicy)
    }

    @Test
    fun `should refuse a group when an invitee has blocked the creator`() {
        val ex = assertThrows<BusinessException> {
            service.createConversation(ConversationType.GROUP, creator, listOf(friend, blocker), "Grup")
        }

        assertEquals(ErrorCode.CONV_INVALID_PARTICIPANTS, ex.errorCode)
        verify(exactly = 0) { conversations.save(any()) }
    }

    @Test
    fun `should create a group when no invitee has blocked the creator`() {
        val result = service.createConversation(ConversationType.GROUP, creator, listOf(friend), "Grup")

        assertEquals(setOf(creator, friend), result.members.map { it.userId }.toSet())
    }

    @Test
    fun `should refuse a channel when the creator tries to enrol other people`() {
        val ex = assertThrows<BusinessException> {
            service.createConversation(ConversationType.CHANNEL, creator, listOf(friend), "Kanal")
        }

        assertEquals(ErrorCode.CONV_INVALID_PARTICIPANTS, ex.errorCode)
        verify(exactly = 0) { conversations.save(any()) }
    }

    @Test
    fun `should make the creator owner and the channel announcement only when a channel is created`() {
        val saved = slot<Conversation>()
        every { conversations.save(capture(saved)) } answers { firstArg() }

        val result = service.createConversation(ConversationType.CHANNEL, creator, emptyList(), "Kanal")

        assertTrue(saved.captured.announcementOnly)
        assertEquals(listOf(ConversationMember(result.conversation.id, creator, MemberRole.OWNER).userId), result.members.map { it.userId })
        assertEquals(MemberRole.OWNER, result.members.single().role)
    }

    @Test
    fun `should refuse a channel when it has no valid name`() {
        val ex = assertThrows<BusinessException> {
            service.createConversation(ConversationType.CHANNEL, creator, emptyList(), " ")
        }

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode)
    }
}
