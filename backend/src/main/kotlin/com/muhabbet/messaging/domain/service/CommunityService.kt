package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Community
import com.muhabbet.messaging.domain.model.CommunityGroup
import com.muhabbet.messaging.domain.model.CommunityMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.`in`.CommunityDetails
import com.muhabbet.messaging.domain.port.`in`.CommunityGroupSummary
import com.muhabbet.messaging.domain.port.`in`.CommunitySummary
import com.muhabbet.messaging.domain.port.`in`.ManageCommunityUseCase
import com.muhabbet.messaging.domain.port.out.CommunityRepository
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

open class CommunityService(
    private val communityRepository: CommunityRepository,
    private val conversationRepository: ConversationRepository
) : ManageCommunityUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun create(name: String, description: String?, creatorId: UUID): CommunitySummary {
        val community = Community(
            name = name,
            description = description,
            createdBy = creatorId
        )
        val saved = communityRepository.save(community)

        // Add creator as OWNER
        communityRepository.saveMember(
            CommunityMember(communityId = saved.id, userId = creatorId, role = MemberRole.OWNER)
        )

        log.info("Community created: id={}, name={}, creator={}", saved.id, name, creatorId)
        // A community starts with no groups and exactly one member: the creator just added above.
        return CommunitySummary(community = saved, groupCount = 0, memberCount = 1)
    }

    @Transactional
    override fun addGroup(communityId: UUID, conversationId: UUID, userId: UUID): CommunityGroup {
        requireAdminOrOwner(communityId, userId)

        // The conversation id is supplied by the caller, so it is checked against the caller before
        // anything is stored: linking a conversation publishes its name, avatar and member count to
        // everyone who can read the community. The membership lookup deliberately comes first, so a
        // caller who is not in the conversation gets the same answer whether or not it exists.
        conversationRepository.findMember(conversationId, userId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_MEMBER)

        val conversation = conversationRepository.findById(conversationId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_FOUND)
        if (conversation.type != ConversationType.GROUP) {
            throw BusinessException(ErrorCode.COMMUNITY_NOT_A_GROUP)
        }

        val group = CommunityGroup(communityId = communityId, conversationId = conversationId)
        val saved = communityRepository.addGroup(group)
        log.info("Group added to community: community={}, conversation={}", communityId, conversationId)
        return saved
    }

    @Transactional
    override fun removeGroup(communityId: UUID, conversationId: UUID, userId: UUID) {
        requireAdminOrOwner(communityId, userId)
        communityRepository.removeGroup(communityId, conversationId)
        log.info("Group removed from community: community={}, conversation={}", communityId, conversationId)
    }

    @Transactional
    override fun addMember(communityId: UUID, userId: UUID, requesterId: UUID): CommunityMember {
        communityRepository.findById(communityId)
            ?: throw BusinessException(ErrorCode.COMMUNITY_NOT_FOUND)

        requireAdminOrOwner(communityId, requesterId)

        val existing = communityRepository.findMember(communityId, userId)
        if (existing != null) {
            throw BusinessException(ErrorCode.GROUP_ALREADY_MEMBER)
        }

        // Community membership derives from group membership, as it does in WhatsApp: an owner may
        // enrol someone who is already in one of the community's own groups, and nobody else. This
        // is a restriction, not a feature — the real answer is an invite the recipient accepts, and
        // that is #387. Without it, an owner could attach any user id they could guess or read, and
        // the community would appear in that person's Communities tab unannounced.
        val conversationIds = communityRepository.findGroupsByCommunityId(communityId).map { it.conversationId }
        if (!conversationRepository.isMemberOfAny(conversationIds, userId)) {
            throw BusinessException(ErrorCode.COMMUNITY_MEMBER_NOT_IN_ANY_GROUP)
        }

        val member = CommunityMember(communityId = communityId, userId = userId, role = MemberRole.MEMBER)
        val saved = communityRepository.saveMember(member)
        log.info("Member added to community: community={}, user={}", communityId, userId)
        return saved
    }

    @Transactional(readOnly = true)
    override fun getDetails(communityId: UUID, userId: UUID): CommunityDetails {
        val community = communityRepository.findById(communityId)
            ?: throw BusinessException(ErrorCode.COMMUNITY_NOT_FOUND)

        // Members only. There is no discovery, search or invite path in this app, so a community is
        // only ever opened from the caller's own list or straight after creating it — nothing
        // legitimate reads a community the caller does not belong to, and reading one exposes every
        // linked conversation's name, avatar and size.
        val membership = communityRepository.findMember(communityId, userId)
            ?: throw BusinessException(ErrorCode.COMMUNITY_PERMISSION_DENIED)

        val groups = communityRepository.findGroupsByCommunityId(communityId)
        val conversationIds = groups.map { it.conversationId }
        // Two batched lookups instead of two queries per group.
        val conversations = conversationRepository.findConversationsByIds(conversationIds).associateBy { it.id }
        val groupMemberCounts = conversationRepository.countMembersByConversationIds(conversationIds)

        return CommunityDetails(
            community = community,
            groups = groups.map { group ->
                val conversation = conversations[group.conversationId]
                CommunityGroupSummary(
                    conversationId = group.conversationId,
                    name = conversation?.name,
                    avatarUrl = conversation?.avatarUrl,
                    memberCount = groupMemberCounts[group.conversationId] ?: 0
                )
            },
            memberCount = communityRepository.countMembersByCommunityIds(listOf(communityId))[communityId] ?: 0,
            myRole = membership.role
        )
    }

    @Transactional(readOnly = true)
    override fun listForUser(userId: UUID): List<CommunitySummary> {
        val communities = communityRepository.findCommunitiesByUserId(userId)
        if (communities.isEmpty()) return emptyList()

        // Batched so the list costs three queries regardless of how many communities come back.
        val communityIds = communities.map { it.id }
        val groupCounts = communityRepository.countGroupsByCommunityIds(communityIds)
        val memberCounts = communityRepository.countMembersByCommunityIds(communityIds)

        return communities.map { community ->
            CommunitySummary(
                community = community,
                groupCount = groupCounts[community.id] ?: 0,
                memberCount = memberCounts[community.id] ?: 0
            )
        }
    }

    private fun requireAdminOrOwner(communityId: UUID, userId: UUID) {
        val member = communityRepository.findMember(communityId, userId)
            ?: throw BusinessException(ErrorCode.COMMUNITY_PERMISSION_DENIED)
        if (member.role == MemberRole.MEMBER) {
            throw BusinessException(ErrorCode.COMMUNITY_PERMISSION_DENIED)
        }
    }
}
