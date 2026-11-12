package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.GroupInviteLink
import com.muhabbet.messaging.domain.model.GroupJoinRequest
import com.muhabbet.messaging.domain.model.JoinRequestStatus
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.GroupInviteLinkRepository
import com.muhabbet.messaging.domain.port.out.GroupJoinRequestRepository
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
import java.time.Instant
import java.util.UUID

/**
 * A join request is the approval half of an invite link: it can only be filed through a live link
 * of that group that asks for approval, and only the group's admins see or decide the queue.
 */
class JoinRequestServiceTest {

    private lateinit var requests: GroupJoinRequestRepository
    private lateinit var conversations: ConversationRepository
    private lateinit var links: GroupInviteLinkRepository
    private lateinit var service: JoinRequestService

    private val groupId = TestData.GROUP_ID
    private val owner = TestData.USER_ID_1
    private val member = TestData.USER_ID_2
    private val stranger = TestData.USER_ID_3

    private fun link(
        conversationId: UUID = groupId,
        requiresApproval: Boolean = true,
        isActive: Boolean = true,
        expiresAt: Instant? = null
    ) = GroupInviteLink(
        conversationId = conversationId,
        inviteToken = "t",
        createdBy = owner,
        requiresApproval = requiresApproval,
        isActive = isActive,
        expiresAt = expiresAt
    )

    @BeforeEach
    fun setUp() {
        requests = mockk(relaxed = true)
        conversations = mockk(relaxed = true)
        links = mockk(relaxed = true)
        service = JoinRequestService(requests, conversations, links)
        every { conversations.findMember(groupId, owner) } returns TestData.owner(groupId, owner)
        every { conversations.findMember(groupId, member) } returns TestData.member(groupId, member)
        every { conversations.findMember(groupId, stranger) } returns null
        every { requests.findByConversationIdAndUserIdAndStatus(any(), any(), any()) } returns null
        every { requests.save(any()) } answers { firstArg() }
    }

    @Test
    fun `should refuse to list pending requests when the caller is a plain member`() {
        val ex = assertThrows<BusinessException> { service.getPendingRequests(groupId, member) }

        assertEquals(ErrorCode.GROUP_PERMISSION_DENIED, ex.errorCode)
    }

    @Test
    fun `should refuse to list pending requests when the caller is not a member`() {
        val ex = assertThrows<BusinessException> { service.getPendingRequests(groupId, stranger) }

        assertEquals(ErrorCode.GROUP_NOT_MEMBER, ex.errorCode)
    }

    @Test
    fun `should list pending requests when the caller is the owner`() {
        every { requests.findPendingByConversationId(groupId) } returns emptyList()

        assertEquals(emptyList<Any>(), service.getPendingRequests(groupId, owner))
    }

    @Test
    fun `should refuse a join request when no link is given`() {
        val ex = assertThrows<BusinessException> { service.requestJoin(groupId, stranger, null) }

        assertEquals(ErrorCode.INVITE_LINK_NOT_FOUND, ex.errorCode)
        verify(exactly = 0) { requests.save(any()) }
    }

    @Test
    fun `should refuse a join request when the link belongs to another conversation`() {
        val foreign = link(conversationId = UUID.randomUUID())
        every { links.findById(foreign.id) } returns foreign

        val ex = assertThrows<BusinessException> { service.requestJoin(groupId, stranger, foreign.id) }

        assertEquals(ErrorCode.INVITE_LINK_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `should refuse a join request when the link is revoked or does not ask for approval`() {
        val revoked = link(isActive = false)
        val open = link(requiresApproval = false)
        every { links.findById(revoked.id) } returns revoked
        every { links.findById(open.id) } returns open

        assertThrows<BusinessException> { service.requestJoin(groupId, stranger, revoked.id) }
        assertThrows<BusinessException> { service.requestJoin(groupId, stranger, open.id) }
        verify(exactly = 0) { requests.save(any()) }
    }

    @Test
    fun `should refuse a join request when the link has expired`() {
        val expired = link(expiresAt = Instant.now().minusSeconds(60))
        every { links.findById(expired.id) } returns expired

        val ex = assertThrows<BusinessException> { service.requestJoin(groupId, stranger, expired.id) }

        assertEquals(ErrorCode.INVITE_LINK_EXPIRED, ex.errorCode)
    }

    @Test
    fun `should file a join request when the link is live and asks for approval`() {
        val live = link()
        every { links.findById(live.id) } returns live

        val saved = service.requestJoin(groupId, stranger, live.id)

        assertEquals(live.id, saved.inviteLinkId)
    }

    @Test
    fun `should refuse to approve when the request is not pending`() {
        val decided = GroupJoinRequest(conversationId = groupId, userId = stranger, status = JoinRequestStatus.REJECTED)
        every { requests.findById(decided.id) } returns decided

        val ex = assertThrows<BusinessException> { service.approveJoin(groupId, decided.id, owner) }

        assertEquals(ErrorCode.JOIN_REQUEST_NOT_FOUND, ex.errorCode)
        verify(exactly = 0) { conversations.saveMember(any()) }
    }

    @Test
    fun `should refuse to approve when the request belongs to another conversation`() {
        val elsewhere = GroupJoinRequest(conversationId = UUID.randomUUID(), userId = stranger)
        every { requests.findById(elsewhere.id) } returns elsewhere

        val ex = assertThrows<BusinessException> { service.approveJoin(groupId, elsewhere.id, owner) }

        assertEquals(ErrorCode.JOIN_REQUEST_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `should add the member when an admin approves a pending request`() {
        val pending = GroupJoinRequest(conversationId = groupId, userId = stranger)
        every { requests.findById(pending.id) } returns pending

        service.approveJoin(groupId, pending.id, owner)

        verify { conversations.saveMember(match { it.userId == stranger && it.role == MemberRole.MEMBER }) }
    }
}
