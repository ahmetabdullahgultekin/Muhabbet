package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.GroupJoinRequest
import com.muhabbet.messaging.domain.model.JoinRequestStatus
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.GroupInviteLinkRepository
import com.muhabbet.messaging.domain.port.out.GroupJoinRequestRepository
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.messaging.domain.port.out.UserDisplayInfo
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

/**
 * #788: the join-request review flow got its first caller (the group info screen), so the guards it
 * never needed while nothing called it are pinned here.
 */
class JoinRequestServiceTest {

    private val joinRequests = mockk<GroupJoinRequestRepository>(relaxed = true)
    private val conversations = mockk<ConversationRepository>(relaxed = true)
    private val inviteLinks = mockk<GroupInviteLinkRepository>(relaxed = true)
    private val directory = mockk<UserDirectoryPort>()
    private lateinit var service: JoinRequestService

    private val group = TestData.CONVERSATION_ID
    private val admin = TestData.USER_ID_1
    private val member = TestData.USER_ID_2
    private val requester = TestData.USER_ID_3
    private val requestId = UUID.randomUUID()

    private fun pending(conversationId: UUID = group, status: JoinRequestStatus = JoinRequestStatus.PENDING) =
        GroupJoinRequest(id = requestId, conversationId = conversationId, userId = requester, status = status)

    @BeforeEach
    fun setUp() {
        service = JoinRequestService(joinRequests, conversations, inviteLinks, directory)
        every { conversations.findMember(group, admin) } returns ConversationMember(group, admin, MemberRole.ADMIN)
        every { conversations.findMember(group, member) } returns ConversationMember(group, member, MemberRole.MEMBER)
        every { conversations.findMember(group, requester) } returns null
    }

    @Test
    fun `should list pending requests with the requester's name when an admin asks`() {
        every { joinRequests.findPendingByConversationId(group) } returns listOf(pending())
        every { directory.findDisplayInfo(setOf(requester)) } returns
            mapOf(requester to UserDisplayInfo(requester, "Ayşe", "https://cdn/a.jpg"))

        val result = service.getPendingRequests(group, admin)

        assertEquals(1, result.size)
        assertEquals("Ayşe", result.single().displayName)
        assertEquals(requestId, result.single().request.id)
    }

    @Test
    fun `should refuse the pending list when a plain member asks`() {
        val e = assertThrows<BusinessException> { service.getPendingRequests(group, member) }
        assertEquals(ErrorCode.GROUP_PERMISSION_DENIED, e.errorCode)
    }

    @Test
    fun `should refuse the pending list when a non-member asks`() {
        val stranger = UUID.randomUUID()
        every { conversations.findMember(group, stranger) } returns null

        val e = assertThrows<BusinessException> { service.getPendingRequests(group, stranger) }
        assertEquals(ErrorCode.GROUP_NOT_MEMBER, e.errorCode)
        verify(exactly = 0) { joinRequests.findPendingByConversationId(any()) }
    }

    @Test
    fun `should add the requester as a member and mark the request approved when an admin approves`() {
        every { joinRequests.findById(requestId) } returns pending()
        val saved = slot<GroupJoinRequest>()
        every { joinRequests.save(capture(saved)) } answers { saved.captured }

        service.approveJoin(group, requestId, admin)

        assertEquals(JoinRequestStatus.APPROVED, saved.captured.status)
        assertEquals(admin, saved.captured.reviewedBy)
        verify { conversations.saveMember(match { it.userId == requester && it.role == MemberRole.MEMBER }) }
    }

    @Test
    fun `should not add a second membership when the requester was already added by hand`() {
        every { joinRequests.findById(requestId) } returns pending()
        every { conversations.findMember(group, requester) } returns ConversationMember(group, requester)

        service.approveJoin(group, requestId, admin)

        verify(exactly = 0) { conversations.saveMember(any()) }
    }

    @Test
    fun `should report not found when the request belongs to another group`() {
        every { joinRequests.findById(requestId) } returns pending(conversationId = UUID.randomUUID())

        val e = assertThrows<BusinessException> { service.approveJoin(group, requestId, admin) }
        assertEquals(ErrorCode.JOIN_REQUEST_NOT_FOUND, e.errorCode)
        verify(exactly = 0) { conversations.saveMember(any()) }
    }

    @Test
    fun `should not approve a request that was already rejected`() {
        every { joinRequests.findById(requestId) } returns pending(status = JoinRequestStatus.REJECTED)

        val e = assertThrows<BusinessException> { service.approveJoin(group, requestId, admin) }
        assertEquals(ErrorCode.JOIN_REQUEST_NOT_FOUND, e.errorCode)
        verify(exactly = 0) { conversations.saveMember(any()) }
    }

    @Test
    fun `should refuse approval when a plain member tries`() {
        every { joinRequests.findById(requestId) } returns pending()

        val e = assertThrows<BusinessException> { service.approveJoin(group, requestId, member) }
        assertEquals(ErrorCode.GROUP_PERMISSION_DENIED, e.errorCode)
    }

    @Test
    fun `should mark the request rejected without adding anyone when an admin rejects`() {
        every { joinRequests.findById(requestId) } returns pending()
        val saved = slot<GroupJoinRequest>()
        every { joinRequests.save(capture(saved)) } answers { saved.captured }

        service.rejectJoin(group, requestId, admin)

        assertEquals(JoinRequestStatus.REJECTED, saved.captured.status)
        verify(exactly = 0) { conversations.saveMember(any()) }
    }
}
