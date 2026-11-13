package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.GroupInviteLink
import com.muhabbet.messaging.domain.model.GroupJoinRequest
import com.muhabbet.messaging.domain.model.JoinRequestReview
import com.muhabbet.messaging.domain.model.JoinRequestStatus
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.GroupInviteLinkRepository
import com.muhabbet.messaging.domain.port.out.GroupJoinRequestRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.shared.InlineTransactionRunner
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.validation.ValidationRules
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * #888: the join-request lifecycle had no guards and `JoinRequestService` had no tests.
 */
class JoinRequestServiceTest {

    private val joinRequestRepository = mockk<GroupJoinRequestRepository>()
    private val conversationRepository = mockk<ConversationRepository>()
    private val inviteLinkRepository = mockk<GroupInviteLinkRepository>()
    private val userDirectory = mockk<UserDirectoryPort>()
    private val messageBroadcaster = mockk<MessageBroadcaster>(relaxed = true)
    private val now = Instant.parse("2026-11-13T12:00:00Z")
    private lateinit var service: JoinRequestService

    private val groupId = TestData.GROUP_ID
    private val ownerId = TestData.USER_ID_1
    private val memberId = TestData.USER_ID_2
    private val applicantId = TestData.USER_ID_3
    private val linkId = UUID.randomUUID()

    private fun link(
        conversationId: UUID = groupId,
        requiresApproval: Boolean = true,
        isActive: Boolean = true,
        expiresAt: Instant? = null
    ) = GroupInviteLink(
        id = linkId, conversationId = conversationId, inviteToken = "t", createdBy = ownerId,
        requiresApproval = requiresApproval, isActive = isActive, expiresAt = expiresAt
    )

    private fun request(status: JoinRequestStatus = JoinRequestStatus.PENDING) = GroupJoinRequest(
        id = UUID.randomUUID(), conversationId = groupId, userId = applicantId, inviteLinkId = linkId, status = status
    )

    @BeforeEach
    fun setUp() {
        service = JoinRequestService(
            joinRequestRepository, conversationRepository, inviteLinkRepository,
            GroupAdmission(conversationRepository, userDirectory, messageBroadcaster),
            InlineTransactionRunner(),
            Clock.fixed(now, ZoneOffset.UTC)
        )
        every { conversationRepository.findById(groupId) } returns TestData.groupConversation(id = groupId)
        every { conversationRepository.findMember(groupId, ownerId) } returns TestData.owner(groupId, ownerId)
        every { conversationRepository.findMember(groupId, memberId) } returns
            TestData.member(conversationId = groupId, userId = memberId, role = MemberRole.MEMBER)
        every { conversationRepository.findMember(groupId, applicantId) } returns null
        every { conversationRepository.findMembersByConversationId(groupId) } returns listOf(TestData.owner(groupId, ownerId))
        every { conversationRepository.saveMember(any()) } answers { firstArg() }
        every { userDirectory.findDisplayInfo(any()) } returns emptyMap()
        every { joinRequestRepository.markReviewed(any()) } returns true
        every { inviteLinkRepository.claimUse(linkId) } returns true
    }

    @Nested
    inner class Transitions {

        @Test
        fun `should move a pending request to APPROVED with reviewer and time`() {
            val reviewed = request().approve(ownerId, now)

            val approved = (reviewed as JoinRequestReview.Reviewed).request
            assertEquals(JoinRequestStatus.APPROVED, approved.status)
            assertEquals(ownerId, approved.reviewedBy)
            assertEquals(now, approved.reviewedAt)
        }

        @Test
        fun `should move a pending request to REJECTED`() {
            val reviewed = request().reject(ownerId, now)

            assertEquals(JoinRequestStatus.REJECTED, (reviewed as JoinRequestReview.Reviewed).request.status)
        }

        @ParameterizedTest
        @EnumSource(value = JoinRequestStatus::class, names = ["APPROVED", "REJECTED"])
        fun `should refuse to review a request that is no longer pending`(status: JoinRequestStatus) {
            assertEquals(JoinRequestReview.AlreadyReviewed(status), request(status).approve(ownerId, now))
            assertEquals(JoinRequestReview.AlreadyReviewed(status), request(status).reject(ownerId, now))
        }
    }

    @Nested
    inner class Review {

        @ParameterizedTest
        @EnumSource(value = JoinRequestStatus::class, names = ["APPROVED", "REJECTED"])
        fun `should refuse approving a request that was already reviewed`(status: JoinRequestStatus) {
            val stale = request(status)
            every { joinRequestRepository.findById(stale.id) } returns stale

            val ex = assertThrows<BusinessException> { service.approveJoin(stale.id, ownerId) }

            assertEquals(ErrorCode.JOIN_REQUEST_ALREADY_REVIEWED, ex.errorCode)
            verify(exactly = 0) { conversationRepository.saveMember(any()) }
        }

        @Test
        fun `should refuse rejecting a request that was already approved`() {
            val stale = request(JoinRequestStatus.APPROVED)
            every { joinRequestRepository.findById(stale.id) } returns stale

            val ex = assertThrows<BusinessException> { service.rejectJoin(stale.id, ownerId) }

            assertEquals(ErrorCode.JOIN_REQUEST_ALREADY_REVIEWED, ex.errorCode)
            verify(exactly = 0) { joinRequestRepository.markReviewed(any()) }
        }

        @Test
        fun `should refuse the loser of two concurrent approvals`() {
            val pending = request()
            every { joinRequestRepository.findById(pending.id) } returns pending
            every { joinRequestRepository.markReviewed(any()) } returns false

            val ex = assertThrows<BusinessException> { service.approveJoin(pending.id, ownerId) }

            assertEquals(ErrorCode.JOIN_REQUEST_ALREADY_REVIEWED, ex.errorCode)
            verify(exactly = 0) { conversationRepository.saveMember(any()) }
        }

        @Test
        fun `should admit the applicant, take a link use and tell the group`() {
            val pending = request()
            every { joinRequestRepository.findById(pending.id) } returns pending

            service.approveJoin(pending.id, ownerId)

            verify { conversationRepository.saveMember(match { it.userId == applicantId }) }
            verify { inviteLinkRepository.claimUse(linkId) }
            verify {
                messageBroadcaster.broadcastToUsers(
                    match { it.toSet() == setOf(ownerId, applicantId) },
                    match { it is WsMessage.GroupMemberAdded && it.addedBy == ownerId.toString() }
                )
            }
        }

        @Test
        fun `should refuse an approval into a full group`() {
            val pending = request()
            every { joinRequestRepository.findById(pending.id) } returns pending
            every { conversationRepository.findMembersByConversationId(groupId) } returns
                (1..ValidationRules.MAX_GROUP_MEMBERS).map {
                    TestData.member(conversationId = groupId, userId = UUID.randomUUID(), role = MemberRole.MEMBER)
                }

            val ex = assertThrows<BusinessException> { service.approveJoin(pending.id, ownerId) }

            assertEquals(ErrorCode.CONV_MAX_MEMBERS, ex.errorCode)
            verify(exactly = 0) { messageBroadcaster.broadcastToUsers(any(), any()) }
        }

        @Test
        fun `should refuse an approval by a plain member`() {
            val pending = request()
            every { joinRequestRepository.findById(pending.id) } returns pending

            val ex = assertThrows<BusinessException> { service.approveJoin(pending.id, memberId) }

            assertEquals(ErrorCode.GROUP_PERMISSION_DENIED, ex.errorCode)
        }
    }

    @Nested
    inner class Filing {

        @Test
        fun `should file a request against an approval link of the group`() {
            every { inviteLinkRepository.findById(linkId) } returns link()
            every { joinRequestRepository.findByConversationIdAndUserIdAndStatus(groupId, applicantId, JoinRequestStatus.PENDING) } returns null
            every { joinRequestRepository.save(any()) } answers { firstArg() }

            val saved = service.requestJoin(groupId, applicantId, linkId)

            assertEquals(linkId, saved.inviteLinkId)
            assertEquals(JoinRequestStatus.PENDING, saved.status)
        }

        @Test
        fun `should refuse a request with no link`() {
            val ex = assertThrows<BusinessException> { service.requestJoin(groupId, applicantId, null) }

            assertEquals(ErrorCode.INVITE_LINK_NOT_FOUND, ex.errorCode)
        }

        @Test
        fun `should refuse a request carrying another group's link`() {
            every { inviteLinkRepository.findById(linkId) } returns link(conversationId = UUID.randomUUID())

            val ex = assertThrows<BusinessException> { service.requestJoin(groupId, applicantId, linkId) }

            assertEquals(ErrorCode.INVITE_LINK_NOT_FOUND, ex.errorCode)
        }

        @Test
        fun `should refuse a request on a revoked or expired link`() {
            every { inviteLinkRepository.findById(linkId) } returns link(isActive = false)
            assertEquals(
                ErrorCode.INVITE_LINK_NOT_FOUND,
                assertThrows<BusinessException> { service.requestJoin(groupId, applicantId, linkId) }.errorCode
            )

            every { inviteLinkRepository.findById(linkId) } returns link(expiresAt = now.minusSeconds(1))
            assertEquals(
                ErrorCode.INVITE_LINK_NOT_FOUND,
                assertThrows<BusinessException> { service.requestJoin(groupId, applicantId, linkId) }.errorCode
            )
        }

        @Test
        fun `should refuse a request on a link that admits without approval`() {
            every { inviteLinkRepository.findById(linkId) } returns link(requiresApproval = false)

            val ex = assertThrows<BusinessException> { service.requestJoin(groupId, applicantId, linkId) }

            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode)
        }
    }

    @Nested
    inner class PendingList {

        @Test
        fun `should list pending requests to an admin`() {
            every { joinRequestRepository.findPendingByConversationId(groupId) } returns listOf(request())

            assertTrue(service.getPendingRequests(groupId, ownerId).isNotEmpty())
        }

        @Test
        fun `should refuse the pending list to a plain member`() {
            val ex = assertThrows<BusinessException> { service.getPendingRequests(groupId, memberId) }

            assertEquals(ErrorCode.GROUP_PERMISSION_DENIED, ex.errorCode)
        }

        @Test
        fun `should refuse the pending list to a non-member`() {
            val ex = assertThrows<BusinessException> { service.getPendingRequests(groupId, applicantId) }

            assertEquals(ErrorCode.GROUP_NOT_MEMBER, ex.errorCode)
        }
    }
}
