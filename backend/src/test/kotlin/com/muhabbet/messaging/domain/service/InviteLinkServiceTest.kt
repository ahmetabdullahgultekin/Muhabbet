package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.GroupInviteLink
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
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Reading a group's own invite link — the path the app has always called and the domain never had
 * (#705).
 *
 * The access rule under test is the product decision this endpoint had to make: **membership, not
 * admin rights.** Creating and revoking are the policy acts and stay admin-or-owner; reading is
 * the distribution act, and a member who cannot see the link cannot invite anyone with it, which
 * would leave the feature indistinguishable from `addMember`. The last test pins the boundary that
 * is left — a non-member gets nothing.
 */
class InviteLinkServiceTest {

    private val inviteLinkRepository = mockk<GroupInviteLinkRepository>()
    private val joinRequestRepository = mockk<GroupJoinRequestRepository>()
    private val conversationRepository = mockk<ConversationRepository>()
    private lateinit var service: InviteLinkService

    private val groupId = TestData.GROUP_ID
    private val ownerId = TestData.USER_ID_1
    private val memberId = TestData.USER_ID_2
    private val strangerId = TestData.USER_ID_3
    private val createdAt = Instant.parse("2026-01-01T00:00:00Z")

    @BeforeEach
    fun setUp() {
        service = InviteLinkService(inviteLinkRepository, joinRequestRepository, conversationRepository)
        every { conversationRepository.findById(groupId) } returns TestData.groupConversation(id = groupId)
    }

    @Test
    fun `should return the group's active link to an admin`() {
        givenRole(ownerId, MemberRole.OWNER)
        every { inviteLinkRepository.findActiveByConversationId(groupId) } returns listOf(link())

        val found = service.getActiveLink(groupId, ownerId)

        assertEquals("token-abc", found.inviteToken)
        assertEquals(groupId, found.conversationId)
    }

    @Test
    fun `should return the group's active link to a plain member`() {
        // The access decision, stated as a test: a member may read what a member is expected to
        // hand out. If this ever flips to admin-or-owner, the invite link stops being an invite
        // link and becomes a second spelling of addMember.
        givenRole(memberId, MemberRole.MEMBER)
        every { inviteLinkRepository.findActiveByConversationId(groupId) } returns listOf(link())

        assertEquals("token-abc", service.getActiveLink(groupId, memberId).inviteToken)
    }

    @Test
    fun `should throw INVITE_LINK_NOT_FOUND when the group has no link yet`() {
        // 404 is the contract with the client, not an implementation detail: InviteLinkRepository
        // maps exactly this status to null and the sheet then offers to create one. Any other
        // status — 405 was the bug — reaches the user as the generic error snackbar.
        givenRole(ownerId, MemberRole.OWNER)
        every { inviteLinkRepository.findActiveByConversationId(groupId) } returns emptyList()

        val ex = assertThrows<BusinessException> { service.getActiveLink(groupId, ownerId) }

        assertEquals(ErrorCode.INVITE_LINK_NOT_FOUND, ex.errorCode)
        assertEquals(404, ex.errorCode.httpStatus.value())
    }

    @Test
    fun `should throw GROUP_NOT_MEMBER when the caller is not in the group`() {
        every { conversationRepository.findMember(groupId, strangerId) } returns null

        val ex = assertThrows<BusinessException> { service.getActiveLink(groupId, strangerId) }

        assertEquals(ErrorCode.GROUP_NOT_MEMBER, ex.errorCode)
        assertEquals(403, ex.errorCode.httpStatus.value())
        // Not merely the wrong answer — the link must never be looked up for an outsider at all.
        verify(exactly = 0) { inviteLinkRepository.findActiveByConversationId(any()) }
    }

    @Test
    fun `should refuse a non-member before revealing whether the conversation is a group`() {
        // Membership is checked before type, so a guessed id answers GROUP_NOT_MEMBER either way
        // rather than distinguishing a DM from a group the caller is outside of.
        val dmId = TestData.CONVERSATION_ID
        every { conversationRepository.findById(dmId) } returns TestData.directConversation(id = dmId)
        every { conversationRepository.findMember(dmId, strangerId) } returns null

        val ex = assertThrows<BusinessException> { service.getActiveLink(dmId, strangerId) }

        assertEquals(ErrorCode.GROUP_NOT_MEMBER, ex.errorCode)
    }

    @Test
    fun `should throw GROUP_NOT_FOUND when the conversation does not exist`() {
        val missing = UUID.fromString("00000000-0000-0000-0000-0000000009ff")
        every { conversationRepository.findById(missing) } returns null

        val ex = assertThrows<BusinessException> { service.getActiveLink(missing, ownerId) }

        assertEquals(ErrorCode.GROUP_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `should reject a direct conversation the caller is in`() {
        val dmId = TestData.CONVERSATION_ID
        every { conversationRepository.findById(dmId) } returns TestData.directConversation(id = dmId)
        every { conversationRepository.findMember(dmId, ownerId) } returns
            TestData.member(conversationId = dmId, userId = ownerId)

        val ex = assertThrows<BusinessException> { service.getActiveLink(dmId, ownerId) }

        // Deliberately not 404: 404 would put a "create a link" button in front of a DM, where
        // createLink answers this same code.
        assertEquals(ErrorCode.GROUP_CANNOT_MODIFY_DIRECT, ex.errorCode)
    }

    @Test
    fun `should pick the newest link when an admin created more than one`() {
        // createLink never deactivates its predecessor, so this is reachable by pressing Create
        // twice, and the out-port returns database order. The sheet shows one link and its Revoke
        // targets the one shown, so the pick has to be stable.
        givenRole(ownerId, MemberRole.OWNER)
        val older = link(id = uuid(1), token = "token-old", createdAt = createdAt)
        val newer = link(id = uuid(2), token = "token-new", createdAt = createdAt.plus(1, ChronoUnit.HOURS))
        every { inviteLinkRepository.findActiveByConversationId(groupId) } returns listOf(older, newer)

        assertEquals("token-new", service.getActiveLink(groupId, ownerId).inviteToken)

        every { inviteLinkRepository.findActiveByConversationId(groupId) } returns listOf(newer, older)

        assertEquals("token-new", service.getActiveLink(groupId, ownerId).inviteToken)
    }

    @Test
    fun `should still require admin rights to create a link`() {
        // The read gate was loosened; the write gates were not. Asserted here because loosening
        // requireAdminOrOwner by accident would be invisible in the read tests above.
        givenRole(memberId, MemberRole.MEMBER)

        val ex = assertThrows<BusinessException> {
            service.createLink(groupId, memberId, requiresApproval = false, maxUses = null, expiresAt = null)
        }

        assertEquals(ErrorCode.GROUP_PERMISSION_DENIED, ex.errorCode)
        verify(exactly = 0) { inviteLinkRepository.save(any()) }
    }

    @Test
    fun `should still require admin rights to revoke a link`() {
        every { inviteLinkRepository.findById(uuid(1)) } returns link(id = uuid(1))
        givenRole(memberId, MemberRole.MEMBER)

        val ex = assertThrows<BusinessException> { service.revokeLink(uuid(1), memberId) }

        assertEquals(ErrorCode.GROUP_PERMISSION_DENIED, ex.errorCode)
        verify(exactly = 0) { inviteLinkRepository.deactivate(any()) }
    }

    private fun givenRole(userId: UUID, role: MemberRole) {
        every { conversationRepository.findMember(groupId, userId) } returns
            TestData.member(conversationId = groupId, userId = userId, role = role)
    }

    private fun uuid(n: Int) = UUID.fromString("00000000-0000-0000-0000-00000000%04d".format(n))

    private fun link(
        id: UUID = uuid(1),
        token: String = "token-abc",
        createdAt: Instant = this.createdAt
    ) = GroupInviteLink(
        id = id,
        conversationId = groupId,
        inviteToken = token,
        createdBy = ownerId,
        createdAt = createdAt
    )
}
