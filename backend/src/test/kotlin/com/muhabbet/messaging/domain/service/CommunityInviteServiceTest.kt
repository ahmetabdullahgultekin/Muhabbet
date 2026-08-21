package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Community
import com.muhabbet.messaging.domain.model.CommunityInviteLink
import com.muhabbet.messaging.domain.model.CommunityMember
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.out.CommunityInviteLinkRepository
import com.muhabbet.messaging.domain.port.out.CommunityRepository
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.messaging.domain.port.out.UserDisplayInfo
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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * The invite path (#387, #416) — the only way a community can gain a member who was not already
 * inside one of its groups.
 *
 * The class this exercises exists because of an arithmetic fact, and the first test below pins that
 * fact so it cannot quietly stop being true: `CommunityService.addMember` refuses anyone outside the
 * community's own groups, so a community with **no groups** has an empty candidate set and could
 * never reach two members. Production is eight communities of exactly one member each.
 *
 * [CommunityAnnouncementChannel] is deliberately a **real instance over the same mocks** rather than
 * a mock of its own. The thing worth asserting is that someone who accepts an invite ends up seated
 * in the community's announcement channel, and a mocked collaborator would assert only that a method
 * was called.
 */
class CommunityInviteServiceTest {

    private val communityRepository = mockk<CommunityRepository>()
    private val conversationRepository = mockk<ConversationRepository>()
    private val inviteLinkRepository = mockk<CommunityInviteLinkRepository>()
    private val userDirectoryPort = mockk<UserDirectoryPort>()
    private lateinit var service: CommunityInviteService

    private val communityId = UUID.randomUUID()
    private val ownerId = UUID.randomUUID()
    private val adminId = UUID.randomUUID()
    private val plainMemberId = UUID.randomUUID()
    private val outsiderId = UUID.randomUUID()
    private val channelId = UUID.randomUUID()
    private val token = "a-token"

    @BeforeEach
    fun setUp() {
        service = CommunityInviteService(
            communityRepository = communityRepository,
            inviteLinkRepository = inviteLinkRepository,
            userDirectoryPort = userDirectoryPort,
            announcementChannel = CommunityAnnouncementChannel(communityRepository, conversationRepository)
        )
        every { communityRepository.findById(communityId) } returns community()
        every { communityRepository.countMembersByCommunityIds(listOf(communityId)) } returns mapOf(communityId to 3)
        every { communityRepository.countGroupsByCommunityIds(listOf(communityId)) } returns mapOf(communityId to 1)
        stubRole(ownerId, MemberRole.OWNER)
        stubRole(adminId, MemberRole.ADMIN)
        stubRole(plainMemberId, MemberRole.MEMBER)
        every { communityRepository.findMember(communityId, outsiderId) } returns null
    }

    /**
     * Not a test of this class, but of the wall this class exists to get around. If `addMember` ever
     * stops refusing an outsider, the invite flow is no longer the *only* consented way in and the
     * reasoning in #387 needs revisiting — this test is where that shows up.
     */
    @Nested
    inner class TheWallThisFeatureGetsAround {

        @Test
        fun `should refuse to add an outsider to a community with no groups`() {
            val communityService = CommunityService(
                communityRepository, conversationRepository, userDirectoryPort,
                CommunityAnnouncementChannel(communityRepository, conversationRepository)
            )
            every { communityRepository.findGroupsByCommunityId(communityId) } returns emptyList()
            every { conversationRepository.isMemberOfAny(emptyList(), outsiderId) } returns false

            val ex = assertThrows<BusinessException> {
                communityService.addMember(communityId, outsiderId, ownerId)
            }

            assertEquals(ErrorCode.COMMUNITY_MEMBER_NOT_IN_ANY_GROUP, ex.errorCode)
            // The point: no invite existed, so nothing could be done about it.
            verify(exactly = 0) { communityRepository.saveMember(any()) }
        }
    }

    @Nested
    inner class CreateLink {

        @BeforeEach
        fun stubSave() {
            every { inviteLinkRepository.countActiveByCommunityId(communityId) } returns 0
            every { inviteLinkRepository.save(any()) } answers { firstArg() }
        }

        @Test
        fun `should mint a link for an owner`() {
            val saved = slot<CommunityInviteLink>()
            every { inviteLinkRepository.save(capture(saved)) } answers { firstArg() }

            val link = service.createLink(communityId, ownerId, maxUses = null, expiresAt = null)

            assertEquals(communityId, link.communityId)
            assertEquals(ownerId, link.createdBy)
            assertTrue(link.isActive)
            assertEquals(0, link.useCount)
            // 32 random bytes, url-safe base64, unpadded — must fit the VARCHAR(64) column.
            assertEquals(43, saved.captured.inviteToken.length)
        }

        @Test
        fun `should mint a link for an admin`() {
            val link = service.createLink(communityId, adminId, maxUses = 5, expiresAt = null)
            assertEquals(5, link.maxUses)
        }

        @Test
        fun `should refuse a plain member`() {
            val ex = assertThrows<BusinessException> {
                service.createLink(communityId, plainMemberId, maxUses = null, expiresAt = null)
            }
            assertEquals(ErrorCode.COMMUNITY_PERMISSION_DENIED, ex.errorCode)
            verify(exactly = 0) { inviteLinkRepository.save(any()) }
        }

        @Test
        fun `should refuse someone who is not in the community at all`() {
            val ex = assertThrows<BusinessException> {
                service.createLink(communityId, outsiderId, maxUses = null, expiresAt = null)
            }
            // Same code a plain member gets: telling a stranger apart from a member would confirm
            // that this community id exists and who is in it.
            assertEquals(ErrorCode.COMMUNITY_PERMISSION_DENIED, ex.errorCode)
        }

        @Test
        fun `should refuse a non-existent community`() {
            val unknown = UUID.randomUUID()
            every { communityRepository.findById(unknown) } returns null

            val ex = assertThrows<BusinessException> {
                service.createLink(unknown, ownerId, maxUses = null, expiresAt = null)
            }
            assertEquals(ErrorCode.COMMUNITY_NOT_FOUND, ex.errorCode)
        }

        @Test
        fun `should refuse a max uses of zero`() {
            val ex = assertThrows<BusinessException> {
                service.createLink(communityId, ownerId, maxUses = 0, expiresAt = null)
            }
            assertEquals(ErrorCode.COMMUNITY_INVITE_INVALID_MAX_USES, ex.errorCode)
        }

        @Test
        fun `should refuse an expiry that has already passed`() {
            val ex = assertThrows<BusinessException> {
                service.createLink(
                    communityId, ownerId,
                    maxUses = null,
                    expiresAt = Instant.now().minus(1, ChronoUnit.HOURS)
                )
            }
            assertEquals(ErrorCode.COMMUNITY_INVITE_INVALID_EXPIRY, ex.errorCode)
        }

        @Test
        fun `should refuse once the community holds the maximum number of active links`() {
            every { inviteLinkRepository.countActiveByCommunityId(communityId) } returns
                CommunityInviteService.MAX_ACTIVE_LINKS_PER_COMMUNITY

            val ex = assertThrows<BusinessException> {
                service.createLink(communityId, ownerId, maxUses = null, expiresAt = null)
            }
            assertEquals(ErrorCode.COMMUNITY_INVITE_LIMIT_REACHED, ex.errorCode)
            verify(exactly = 0) { inviteLinkRepository.save(any()) }
        }

        @Test
        fun `should give two links different tokens`() {
            val tokens = mutableListOf<String>()
            every { inviteLinkRepository.save(any()) } answers {
                firstArg<CommunityInviteLink>().also { tokens += it.inviteToken }
            }

            service.createLink(communityId, ownerId, null, null)
            service.createLink(communityId, ownerId, null, null)

            assertEquals(2, tokens.toSet().size)
        }
    }

    @Nested
    inner class ListLinks {

        @Test
        fun `should list active links for an admin`() {
            every { inviteLinkRepository.findActiveByCommunityId(communityId) } returns listOf(link())

            assertEquals(1, service.listLinks(communityId, adminId).size)
        }

        @Test
        fun `should refuse a plain member`() {
            // A token is a bearer credential: a member who could read the list could admit anyone,
            // which is the authority the list is there to gate.
            val ex = assertThrows<BusinessException> { service.listLinks(communityId, plainMemberId) }
            assertEquals(ErrorCode.COMMUNITY_PERMISSION_DENIED, ex.errorCode)
        }
    }

    @Nested
    inner class RevokeLink {

        @Test
        fun `should revoke a link belonging to the community an owner runs`() {
            val existing = link()
            every { inviteLinkRepository.findById(existing.id) } returns existing
            every { inviteLinkRepository.deactivate(existing.id) } returns Unit

            service.revokeLink(communityId, existing.id, ownerId)

            verify { inviteLinkRepository.deactivate(existing.id) }
        }

        @Test
        fun `should refuse to revoke a link that belongs to a different community`() {
            // Both ids come from the caller. An admin of the community named in the path must not be
            // able to revoke another community's link by naming its id in the body.
            val otherCommunityId = UUID.randomUUID()
            val foreign = link(communityId = otherCommunityId)
            every { inviteLinkRepository.findById(foreign.id) } returns foreign

            val ex = assertThrows<BusinessException> { service.revokeLink(communityId, foreign.id, ownerId) }

            assertEquals(ErrorCode.INVITE_LINK_NOT_FOUND, ex.errorCode)
            verify(exactly = 0) { inviteLinkRepository.deactivate(any()) }
        }

        @Test
        fun `should refuse a plain member`() {
            val existing = link()
            every { inviteLinkRepository.findById(existing.id) } returns existing

            val ex = assertThrows<BusinessException> {
                service.revokeLink(communityId, existing.id, plainMemberId)
            }
            assertEquals(ErrorCode.COMMUNITY_PERMISSION_DENIED, ex.errorCode)
            verify(exactly = 0) { inviteLinkRepository.deactivate(any()) }
        }

        @Test
        fun `should report an unknown link id as not found`() {
            val unknown = UUID.randomUUID()
            every { inviteLinkRepository.findById(unknown) } returns null

            val ex = assertThrows<BusinessException> { service.revokeLink(communityId, unknown, ownerId) }
            assertEquals(ErrorCode.INVITE_LINK_NOT_FOUND, ex.errorCode)
        }
    }

    @Nested
    inner class Preview {

        @BeforeEach
        fun stubDirectory() {
            every { userDirectoryPort.findDisplayInfo(listOf(ownerId)) } returns
                mapOf(ownerId to UserDisplayInfo(ownerId, "Ayşe", null))
        }

        @Test
        fun `should tell an outsider holding the token what they are being offered`() {
            every { inviteLinkRepository.findByToken(token) } returns link()

            val preview = service.preview(token, outsiderId)

            assertEquals(communityId, preview.communityId)
            assertEquals("Mahalle", preview.name)
            assertEquals(3, preview.memberCount)
            assertEquals("Ayşe", preview.inviterDisplayName)
            assertFalse(preview.alreadyMember)
        }

        @Test
        fun `should not spend a use of the link`() {
            every { inviteLinkRepository.findByToken(token) } returns link()

            service.preview(token, outsiderId)

            // Opening a link must be free — people tap them twice, and a preview that consumed a use
            // would burn a limited link without anyone joining.
            verify(exactly = 0) { inviteLinkRepository.incrementUseCount(any()) }
            verify(exactly = 0) { communityRepository.saveMember(any()) }
        }

        @Test
        fun `should say when the viewer is already a member`() {
            every { inviteLinkRepository.findByToken(token) } returns link()

            assertTrue(service.preview(token, plainMemberId).alreadyMember)
        }

        @Test
        fun `should report an unknown token as not found`() {
            every { inviteLinkRepository.findByToken("nope") } returns null

            val ex = assertThrows<BusinessException> { service.preview("nope", outsiderId) }
            assertEquals(ErrorCode.INVITE_LINK_NOT_FOUND, ex.errorCode)
        }

        @Test
        fun `should report a revoked link as not found rather than as its own state`() {
            // The holder learns "this does not work", not "this was real once and was withdrawn".
            every { inviteLinkRepository.findByToken(token) } returns link(isActive = false)

            val ex = assertThrows<BusinessException> { service.preview(token, outsiderId) }
            assertEquals(ErrorCode.INVITE_LINK_NOT_FOUND, ex.errorCode)
        }

        @Test
        fun `should report an expired link as expired`() {
            every { inviteLinkRepository.findByToken(token) } returns
                link(expiresAt = Instant.now().minus(1, ChronoUnit.HOURS))

            val ex = assertThrows<BusinessException> { service.preview(token, outsiderId) }
            assertEquals(ErrorCode.INVITE_LINK_EXPIRED, ex.errorCode)
        }

        @Test
        fun `should report an exhausted link as used up`() {
            every { inviteLinkRepository.findByToken(token) } returns link(maxUses = 2, useCount = 2)

            val ex = assertThrows<BusinessException> { service.preview(token, outsiderId) }
            assertEquals(ErrorCode.INVITE_LINK_MAX_USES, ex.errorCode)
        }

        @Test
        fun `should accept a link that still has uses left`() {
            every { inviteLinkRepository.findByToken(token) } returns link(maxUses = 2, useCount = 1)

            assertNotNull(service.preview(token, outsiderId))
        }
    }

    @Nested
    inner class Accept {

        @BeforeEach
        fun stubJoin() {
            every { inviteLinkRepository.findByToken(token) } returns link()
            every { inviteLinkRepository.incrementUseCount(any()) } returns Unit
            every { communityRepository.saveMember(any()) } answers { firstArg() }
            every { conversationRepository.saveMember(any()) } answers { firstArg() }
        }

        @Test
        fun `should make an outsider a member of the community`() {
            val saved = slot<CommunityMember>()
            every { communityRepository.saveMember(capture(saved)) } answers { firstArg() }

            val joined = service.accept(token, outsiderId)

            assertEquals(communityId, saved.captured.communityId)
            assertEquals(outsiderId, saved.captured.userId)
            // Never an admin. The link admits you to the room; it does not hand you the keys.
            assertEquals(MemberRole.MEMBER, saved.captured.role)
            assertEquals(communityId, joined.community.id)
        }

        @Test
        fun `should seat the new member in the announcement channel`() {
            // Otherwise joining lands you in an empty container with nothing to read — the exact
            // complaint #584 was filed about, reintroduced through the new door.
            val seated = slot<ConversationMember>()
            every { conversationRepository.saveMember(capture(seated)) } answers { firstArg() }

            service.accept(token, outsiderId)

            assertEquals(channelId, seated.captured.conversationId)
            assertEquals(outsiderId, seated.captured.userId)
            assertEquals(MemberRole.MEMBER, seated.captured.role)
        }

        @Test
        fun `should spend a use of the link`() {
            val existing = link()
            every { inviteLinkRepository.findByToken(token) } returns existing

            service.accept(token, outsiderId)

            verify { inviteLinkRepository.incrementUseCount(existing.id) }
        }

        @Test
        fun `should refuse someone who is already a member`() {
            val ex = assertThrows<BusinessException> { service.accept(token, plainMemberId) }

            assertEquals(ErrorCode.GROUP_ALREADY_MEMBER, ex.errorCode)
            // And crucially, must not burn a use of a limited link for a no-op join.
            verify(exactly = 0) { inviteLinkRepository.incrementUseCount(any()) }
            verify(exactly = 0) { communityRepository.saveMember(any()) }
        }

        @Test
        fun `should not spend a use when the link is expired`() {
            every { inviteLinkRepository.findByToken(token) } returns
                link(expiresAt = Instant.now().minus(1, ChronoUnit.HOURS))

            assertThrows<BusinessException> { service.accept(token, outsiderId) }

            verify(exactly = 0) { inviteLinkRepository.incrementUseCount(any()) }
            verify(exactly = 0) { communityRepository.saveMember(any()) }
        }

        @Test
        fun `should refuse a link whose uses are exhausted`() {
            every { inviteLinkRepository.findByToken(token) } returns link(maxUses = 1, useCount = 1)

            val ex = assertThrows<BusinessException> { service.accept(token, outsiderId) }

            assertEquals(ErrorCode.INVITE_LINK_MAX_USES, ex.errorCode)
            verify(exactly = 0) { communityRepository.saveMember(any()) }
        }

        @Test
        fun `should build the announcement channel for a community that predates it`() {
            // The eight rows already in production have announcement_group_id = NULL. Someone
            // accepting an invite into one must still land somewhere.
            every { communityRepository.findById(communityId) } returns community(announcementGroupId = null)
            every { communityRepository.findMembersByCommunityId(communityId) } returns
                listOf(CommunityMember(communityId = communityId, userId = ownerId, role = MemberRole.OWNER))
            every { communityRepository.update(any()) } answers { firstArg() }
            every { conversationRepository.updateMemberRole(any(), any(), any()) } returns Unit
            val created = slot<Conversation>()
            every { conversationRepository.save(capture(created)) } answers { firstArg() }

            val joined = service.accept(token, outsiderId)

            assertTrue(created.captured.announcementOnly)
            assertEquals(created.captured.id, joined.community.announcementGroupId)
        }
    }

    // ─── fixtures ───────────────────────────────────────

    private fun stubRole(userId: UUID, role: MemberRole) {
        every { communityRepository.findMember(communityId, userId) } returns
            CommunityMember(communityId = communityId, userId = userId, role = role)
    }

    private fun community(
        announcementGroupId: UUID? = channelId
    ) = Community(
        id = communityId,
        name = "Mahalle",
        description = "Komşular",
        createdBy = ownerId,
        announcementGroupId = announcementGroupId
    )

    private fun link(
        communityId: UUID = this.communityId,
        isActive: Boolean = true,
        maxUses: Int? = null,
        useCount: Int = 0,
        expiresAt: Instant? = null
    ) = CommunityInviteLink(
        communityId = communityId,
        inviteToken = token,
        createdBy = ownerId,
        isActive = isActive,
        maxUses = maxUses,
        useCount = useCount,
        expiresAt = expiresAt
    )
}
