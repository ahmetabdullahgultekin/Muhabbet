package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.TransactionRunner
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Who may change a conversation's disappearing-message timer, and to what.
 *
 * The timer deletes every later message in the conversation, so it is a decision about other
 * people's messages: only a member may make it, in a group or channel only an owner or admin, and
 * only to one of the durations the app offers — a one-second timer is a delete-everything switch.
 */
class DisappearTimerAuthorizationTest {

    private lateinit var conversationRepository: ConversationRepository
    private lateinit var service: DisappearingMessageService

    private val alice = TestData.USER_ID_1
    private val bob = TestData.USER_ID_2

    @BeforeEach
    fun setUp() {
        conversationRepository = mockk(relaxed = true)
        val transactions = object : TransactionRunner {
            override fun <T : Any> inTransaction(block: () -> T): T = block()
        }
        service = DisappearingMessageService(conversationRepository, mockk(relaxed = true), mockk(relaxed = true), transactions)
    }

    @Test
    fun `should refuse when the caller is not a member of the conversation`() {
        val direct = TestData.directConversation()
        every { conversationRepository.findById(direct.id) } returns direct
        every { conversationRepository.findMember(direct.id, bob) } returns null

        val ex = assertThrows<BusinessException> { service.setDisappearTimer(direct.id, bob, 86400) }

        assertEquals(ErrorCode.MSG_NOT_MEMBER, ex.errorCode)
        verify(exactly = 0) { conversationRepository.updateConversation(any()) }
    }

    @Test
    fun `should refuse when a plain member changes a group timer`() {
        val group = TestData.groupConversation()
        every { conversationRepository.findById(group.id) } returns group
        every { conversationRepository.findMember(group.id, bob) } returns TestData.member(group.id, bob)

        val ex = assertThrows<BusinessException> { service.setDisappearTimer(group.id, bob, 86400) }

        assertEquals(ErrorCode.GROUP_PERMISSION_DENIED, ex.errorCode)
        verify(exactly = 0) { conversationRepository.updateConversation(any()) }
    }

    @Test
    fun `should refuse when a plain subscriber changes a channel timer`() {
        val channel = TestData.channelConversation()
        every { conversationRepository.findById(channel.id) } returns channel
        every { conversationRepository.findMember(channel.id, bob) } returns TestData.member(channel.id, bob)

        val ex = assertThrows<BusinessException> { service.setDisappearTimer(channel.id, bob, 86400) }

        assertEquals(ErrorCode.GROUP_PERMISSION_DENIED, ex.errorCode)
    }

    @Test
    fun `should allow an admin when changing a group timer`() {
        val group = TestData.groupConversation()
        every { conversationRepository.findById(group.id) } returns group
        every { conversationRepository.findMember(group.id, bob) } returns
            TestData.member(group.id, bob, role = MemberRole.ADMIN)

        service.setDisappearTimer(group.id, bob, 604800)

        verify { conversationRepository.updateConversation(match { it.disappearAfterSeconds == 604800 }) }
    }

    @Test
    fun `should allow either member when changing a direct timer or turning it off`() {
        val direct = TestData.directConversation()
        every { conversationRepository.findById(direct.id) } returns direct
        every { conversationRepository.findMember(direct.id, bob) } returns TestData.member(direct.id, bob)

        service.setDisappearTimer(direct.id, bob, null)

        verify { conversationRepository.updateConversation(match { it.disappearAfterSeconds == null }) }
    }

    @ParameterizedTest
    @ValueSource(ints = [-1, 0, 1, 29, 86401])
    fun `should refuse when the duration is not one the app offers`(seconds: Int) {
        val direct = TestData.directConversation()
        every { conversationRepository.findById(direct.id) } returns direct
        every { conversationRepository.findMember(direct.id, alice) } returns TestData.member(direct.id, alice)

        val ex = assertThrows<BusinessException> { service.setDisappearTimer(direct.id, alice, seconds) }

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode)
        verify(exactly = 0) { conversationRepository.updateConversation(any()) }
    }
}
