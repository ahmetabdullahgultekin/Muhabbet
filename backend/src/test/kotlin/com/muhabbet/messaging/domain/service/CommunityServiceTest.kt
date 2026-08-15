package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Community
import com.muhabbet.messaging.domain.model.CommunityGroup
import com.muhabbet.messaging.domain.model.CommunityMember
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.out.CommunityRepository
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class CommunityServiceTest {

    private val communityRepository = mockk<CommunityRepository>()
    private val conversationRepository = mockk<ConversationRepository>()
    private lateinit var service: CommunityService

    private val userId = UUID.randomUUID()
    private val communityId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        service = CommunityService(communityRepository, conversationRepository)
    }

    @Nested
    inner class Create {

        @Test
        fun `should report one member and no groups when community is created`() {
            every { communityRepository.save(any()) } answers { firstArg() }
            every { communityRepository.saveMember(any()) } answers { firstArg() }

            val summary = service.create("Mahalle", "Komsular", userId)

            assertEquals("Mahalle", summary.community.name)
            assertEquals(0, summary.groupCount)
            assertEquals(1, summary.memberCount)
        }

        @Test
        fun `should add creator as owner when community is created`() {
            every { communityRepository.save(any()) } answers { firstArg() }
            every { communityRepository.saveMember(any()) } answers { firstArg() }

            val summary = service.create("Mahalle", null, userId)

            verify {
                communityRepository.saveMember(
                    match { it.communityId == summary.community.id && it.userId == userId && it.role == MemberRole.OWNER }
                )
            }
        }
    }

    @Nested
    inner class ListForUser {

        @Test
        fun `should attach group and member counts to every community`() {
            val first = community(name = "Mahalle")
            val second = community(name = "Okul")
            every { communityRepository.findCommunitiesByUserId(userId) } returns listOf(first, second)
            every { communityRepository.countGroupsByCommunityIds(listOf(first.id, second.id)) } returns
                mapOf(first.id to 3, second.id to 1)
            every { communityRepository.countMembersByCommunityIds(listOf(first.id, second.id)) } returns
                mapOf(first.id to 42, second.id to 7)

            val summaries = service.listForUser(userId)

            assertEquals(2, summaries.size)
            assertEquals(3, summaries[0].groupCount)
            assertEquals(42, summaries[0].memberCount)
            assertEquals(1, summaries[1].groupCount)
            assertEquals(7, summaries[1].memberCount)
        }

        @Test
        fun `should count once for the whole list rather than once per community`() {
            val communities = List(5) { community() }
            val ids = communities.map { it.id }
            every { communityRepository.findCommunitiesByUserId(userId) } returns communities
            every { communityRepository.countGroupsByCommunityIds(ids) } returns emptyMap()
            every { communityRepository.countMembersByCommunityIds(ids) } returns emptyMap()

            service.listForUser(userId)

            verify(exactly = 1) { communityRepository.countGroupsByCommunityIds(ids) }
            verify(exactly = 1) { communityRepository.countMembersByCommunityIds(ids) }
        }

        @Test
        fun `should report zero when a community has no groups or members`() {
            val only = community()
            every { communityRepository.findCommunitiesByUserId(userId) } returns listOf(only)
            every { communityRepository.countGroupsByCommunityIds(listOf(only.id)) } returns emptyMap()
            every { communityRepository.countMembersByCommunityIds(listOf(only.id)) } returns emptyMap()

            val summaries = service.listForUser(userId)

            assertEquals(0, summaries[0].groupCount)
            assertEquals(0, summaries[0].memberCount)
        }

        @Test
        fun `should not query counts when user has no communities`() {
            every { communityRepository.findCommunitiesByUserId(userId) } returns emptyList()

            assertTrue(service.listForUser(userId).isEmpty())

            verify(exactly = 0) { communityRepository.countGroupsByCommunityIds(any()) }
            verify(exactly = 0) { communityRepository.countMembersByCommunityIds(any()) }
        }
    }

    @Nested
    inner class GetDetails {

        private val conversationId = UUID.randomUUID()

        @Test
        fun `should carry each group's name avatar and member count`() {
            val community = community()
            stubDetails(
                community = community,
                groups = listOf(CommunityGroup(communityId = community.id, conversationId = conversationId)),
                conversations = listOf(
                    Conversation(
                        id = conversationId,
                        type = ConversationType.GROUP,
                        name = "Bahçe Katı",
                        avatarUrl = "https://cdn.example/g.jpg"
                    )
                ),
                groupMemberCounts = mapOf(conversationId to 12),
                communityMemberCount = 30,
                myRole = MemberRole.ADMIN
            )

            val details = service.getDetails(community.id, userId)

            assertEquals(1, details.groups.size)
            val group = details.groups.first()
            assertEquals(conversationId, group.conversationId)
            assertEquals("Bahçe Katı", group.name)
            assertEquals("https://cdn.example/g.jpg", group.avatarUrl)
            assertEquals(12, group.memberCount)
            assertEquals(30, details.memberCount)
            assertEquals(MemberRole.ADMIN, details.myRole)
        }

        @Test
        fun `should reject the read when caller is not a member of the community`() {
            val community = community()
            stubDetails(community = community, myRole = null)

            val ex = assertThrows<BusinessException> { service.getDetails(community.id, userId) }

            assertEquals(ErrorCode.COMMUNITY_PERMISSION_DENIED, ex.errorCode)
        }

        @Test
        fun `should disclose no conversation metadata when caller is not a member`() {
            val community = community()
            stubDetails(
                community = community,
                groups = listOf(CommunityGroup(communityId = community.id, conversationId = conversationId)),
                conversations = listOf(
                    Conversation(id = conversationId, type = ConversationType.GROUP, name = "Bahçe Katı")
                ),
                myRole = null
            )

            assertThrows<BusinessException> { service.getDetails(community.id, userId) }

            verify(exactly = 0) { conversationRepository.findConversationsByIds(any()) }
            verify(exactly = 0) { conversationRepository.countMembersByConversationIds(any()) }
        }

        @Test
        fun `should fetch conversations in one batch rather than one per group`() {
            val community = community()
            val conversationIds = List(4) { UUID.randomUUID() }
            stubDetails(
                community = community,
                groups = conversationIds.map { CommunityGroup(communityId = community.id, conversationId = it) },
                conversations = conversationIds.map { Conversation(id = it, type = ConversationType.GROUP, name = "G") },
                groupMemberCounts = conversationIds.associateWith { 2 }
            )

            service.getDetails(community.id, userId)

            verify(exactly = 1) { conversationRepository.findConversationsByIds(conversationIds) }
            verify(exactly = 1) { conversationRepository.countMembersByConversationIds(conversationIds) }
        }

        @Test
        fun `should fall back to null name when the group conversation is missing`() {
            val community = community()
            stubDetails(
                community = community,
                groups = listOf(CommunityGroup(communityId = community.id, conversationId = conversationId)),
                conversations = emptyList(),
                groupMemberCounts = emptyMap()
            )

            val group = service.getDetails(community.id, userId).groups.single()

            assertNull(group.name)
            assertNull(group.avatarUrl)
            assertEquals(0, group.memberCount)
        }

        @Test
        fun `should throw COMMUNITY_NOT_FOUND when community does not exist`() {
            every { communityRepository.findById(communityId) } returns null

            val ex = assertThrows<BusinessException> { service.getDetails(communityId, userId) }
            assertEquals(ErrorCode.COMMUNITY_NOT_FOUND, ex.errorCode)
        }

        private fun stubDetails(
            community: Community,
            groups: List<CommunityGroup> = emptyList(),
            conversations: List<Conversation> = emptyList(),
            groupMemberCounts: Map<UUID, Int> = emptyMap(),
            communityMemberCount: Int = 1,
            myRole: MemberRole? = MemberRole.MEMBER
        ) {
            val conversationIds = groups.map { it.conversationId }
            every { communityRepository.findById(community.id) } returns community
            every { communityRepository.findGroupsByCommunityId(community.id) } returns groups
            every { conversationRepository.findConversationsByIds(conversationIds) } returns conversations
            every { conversationRepository.countMembersByConversationIds(conversationIds) } returns groupMemberCounts
            every { communityRepository.countMembersByCommunityIds(listOf(community.id)) } returns
                mapOf(community.id to communityMemberCount)
            every { communityRepository.findMember(community.id, userId) } returns
                myRole?.let { CommunityMember(communityId = community.id, userId = userId, role = it) }
        }
    }

    @Nested
    inner class AddGroup {

        private val conversationId = UUID.randomUUID()

        @Test
        fun `should link the conversation when caller runs the community and belongs to the group`() {
            stubCommunityRole(MemberRole.ADMIN)
            stubConversation(ConversationType.GROUP, callerIsMember = true)
            every { communityRepository.addGroup(any()) } answers { firstArg() }

            val group = service.addGroup(communityId, conversationId, userId)

            assertEquals(communityId, group.communityId)
            assertEquals(conversationId, group.conversationId)
        }

        @Test
        fun `should reject when caller does not belong to the conversation`() {
            stubCommunityRole(MemberRole.OWNER)
            stubConversation(ConversationType.GROUP, callerIsMember = false)

            val ex = assertThrows<BusinessException> { service.addGroup(communityId, conversationId, userId) }

            assertEquals(ErrorCode.GROUP_NOT_MEMBER, ex.errorCode)
            verify(exactly = 0) { communityRepository.addGroup(any()) }
        }

        @Test
        fun `should not reveal whether the conversation exists when caller does not belong to it`() {
            stubCommunityRole(MemberRole.OWNER)
            stubConversation(ConversationType.GROUP, callerIsMember = false)

            assertThrows<BusinessException> { service.addGroup(communityId, conversationId, userId) }

            verify(exactly = 0) { conversationRepository.findById(any()) }
        }

        @Test
        fun `should reject when the conversation is a direct chat`() {
            stubCommunityRole(MemberRole.OWNER)
            stubConversation(ConversationType.DIRECT, callerIsMember = true)

            val ex = assertThrows<BusinessException> { service.addGroup(communityId, conversationId, userId) }

            assertEquals(ErrorCode.COMMUNITY_NOT_A_GROUP, ex.errorCode)
            verify(exactly = 0) { communityRepository.addGroup(any()) }
        }

        @Test
        fun `should reject when the conversation is a channel`() {
            stubCommunityRole(MemberRole.OWNER)
            stubConversation(ConversationType.CHANNEL, callerIsMember = true)

            val ex = assertThrows<BusinessException> { service.addGroup(communityId, conversationId, userId) }

            assertEquals(ErrorCode.COMMUNITY_NOT_A_GROUP, ex.errorCode)
        }

        @Test
        fun `should reject when the conversation does not exist`() {
            stubCommunityRole(MemberRole.OWNER)
            every { conversationRepository.findMember(conversationId, userId) } returns
                ConversationMember(conversationId = conversationId, userId = userId)
            every { conversationRepository.findById(conversationId) } returns null

            val ex = assertThrows<BusinessException> { service.addGroup(communityId, conversationId, userId) }

            assertEquals(ErrorCode.GROUP_NOT_FOUND, ex.errorCode)
        }

        @Test
        fun `should reject when caller is only a plain member of the community`() {
            stubCommunityRole(MemberRole.MEMBER)

            val ex = assertThrows<BusinessException> { service.addGroup(communityId, conversationId, userId) }

            assertEquals(ErrorCode.COMMUNITY_PERMISSION_DENIED, ex.errorCode)
            verify(exactly = 0) { conversationRepository.findMember(any(), any()) }
        }

        @Test
        fun `should reject when caller is not in the community at all`() {
            stubCommunityRole(null)

            val ex = assertThrows<BusinessException> { service.addGroup(communityId, conversationId, userId) }

            assertEquals(ErrorCode.COMMUNITY_PERMISSION_DENIED, ex.errorCode)
            verify(exactly = 0) { communityRepository.addGroup(any()) }
        }

        private fun stubConversation(type: ConversationType, callerIsMember: Boolean) {
            every { conversationRepository.findMember(conversationId, userId) } returns
                if (callerIsMember) ConversationMember(conversationId = conversationId, userId = userId) else null
            every { conversationRepository.findById(conversationId) } returns
                Conversation(id = conversationId, type = type, name = "Bahçe Katı")
        }
    }

    @Nested
    inner class RemoveGroup {

        private val conversationId = UUID.randomUUID()

        @Test
        fun `should unlink the conversation when caller runs the community`() {
            stubCommunityRole(MemberRole.ADMIN)
            every { communityRepository.removeGroup(communityId, conversationId) } returns Unit

            service.removeGroup(communityId, conversationId, userId)

            verify(exactly = 1) { communityRepository.removeGroup(communityId, conversationId) }
        }

        @Test
        fun `should reject when caller is only a plain member of the community`() {
            stubCommunityRole(MemberRole.MEMBER)

            val ex = assertThrows<BusinessException> { service.removeGroup(communityId, conversationId, userId) }

            assertEquals(ErrorCode.COMMUNITY_PERMISSION_DENIED, ex.errorCode)
            verify(exactly = 0) { communityRepository.removeGroup(any(), any()) }
        }
    }

    @Nested
    inner class AddMember {

        private val targetUserId = UUID.randomUUID()
        private val groupConversationId = UUID.randomUUID()

        @Test
        fun `should enrol the user when they already belong to one of the community's groups`() {
            stubAddMember(groups = listOf(groupConversationId), targetIsInAGroup = true)
            every { communityRepository.saveMember(any()) } answers { firstArg() }

            val member = service.addMember(communityId, targetUserId, userId)

            assertEquals(communityId, member.communityId)
            assertEquals(targetUserId, member.userId)
            assertEquals(MemberRole.MEMBER, member.role)
        }

        @Test
        fun `should reject when the user belongs to none of the community's groups`() {
            stubAddMember(groups = listOf(groupConversationId), targetIsInAGroup = false)

            val ex = assertThrows<BusinessException> { service.addMember(communityId, targetUserId, userId) }

            assertEquals(ErrorCode.COMMUNITY_MEMBER_NOT_IN_ANY_GROUP, ex.errorCode)
            verify(exactly = 0) { communityRepository.saveMember(any()) }
        }

        @Test
        fun `should reject when the community has no groups yet`() {
            stubAddMember(groups = emptyList(), targetIsInAGroup = false)

            val ex = assertThrows<BusinessException> { service.addMember(communityId, targetUserId, userId) }

            assertEquals(ErrorCode.COMMUNITY_MEMBER_NOT_IN_ANY_GROUP, ex.errorCode)
            verify(exactly = 0) { communityRepository.saveMember(any()) }
        }

        @Test
        fun `should reject when caller is only a plain member of the community`() {
            every { communityRepository.findById(communityId) } returns community(id = communityId)
            stubCommunityRole(MemberRole.MEMBER)

            val ex = assertThrows<BusinessException> { service.addMember(communityId, targetUserId, userId) }

            assertEquals(ErrorCode.COMMUNITY_PERMISSION_DENIED, ex.errorCode)
            verify(exactly = 0) { conversationRepository.isMemberOfAny(any(), any()) }
        }

        @Test
        fun `should reject when the user is already a member`() {
            every { communityRepository.findById(communityId) } returns community(id = communityId)
            stubCommunityRole(MemberRole.OWNER)
            every { communityRepository.findMember(communityId, targetUserId) } returns
                CommunityMember(communityId = communityId, userId = targetUserId)

            val ex = assertThrows<BusinessException> { service.addMember(communityId, targetUserId, userId) }

            assertEquals(ErrorCode.GROUP_ALREADY_MEMBER, ex.errorCode)
        }

        @Test
        fun `should reject when the community does not exist`() {
            every { communityRepository.findById(communityId) } returns null

            val ex = assertThrows<BusinessException> { service.addMember(communityId, targetUserId, userId) }

            assertEquals(ErrorCode.COMMUNITY_NOT_FOUND, ex.errorCode)
        }

        private fun stubAddMember(groups: List<UUID>, targetIsInAGroup: Boolean) {
            every { communityRepository.findById(communityId) } returns community(id = communityId)
            stubCommunityRole(MemberRole.OWNER)
            every { communityRepository.findMember(communityId, targetUserId) } returns null
            every { communityRepository.findGroupsByCommunityId(communityId) } returns
                groups.map { CommunityGroup(communityId = communityId, conversationId = it) }
            every { conversationRepository.isMemberOfAny(groups, targetUserId) } returns targetIsInAGroup
        }
    }

    /** Stubs the requester's role in [communityId]; `null` means the requester is not a member. */
    private fun stubCommunityRole(role: MemberRole?) {
        every { communityRepository.findMember(communityId, userId) } returns
            role?.let { CommunityMember(communityId = communityId, userId = userId, role = it) }
    }

    private fun community(name: String = "Mahalle", id: UUID = UUID.randomUUID()) = Community(
        id = id,
        name = name,
        description = null,
        createdBy = userId,
        createdAt = Instant.parse("2026-01-01T00:00:00Z")
    )
}
