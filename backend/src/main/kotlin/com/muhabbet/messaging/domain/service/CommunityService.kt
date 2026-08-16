package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Community
import com.muhabbet.messaging.domain.model.CommunityGroup
import com.muhabbet.messaging.domain.model.CommunityMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.`in`.CommunityDetails
import com.muhabbet.messaging.domain.port.`in`.CommunityGroupSummary
import com.muhabbet.messaging.domain.port.`in`.CommunityMemberCandidate
import com.muhabbet.messaging.domain.port.`in`.CommunityMemberSummary
import com.muhabbet.messaging.domain.port.`in`.CommunitySummary
import com.muhabbet.messaging.domain.port.`in`.ManageCommunityMembershipUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageCommunityUseCase
import com.muhabbet.messaging.domain.port.out.CommunityRepository
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.validation.ValidationRules
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

open class CommunityService(
    private val communityRepository: CommunityRepository,
    private val conversationRepository: ConversationRepository,
    private val userDirectoryPort: UserDirectoryPort
) : ManageCommunityUseCase, ManageCommunityMembershipUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun create(name: String, description: String?, creatorId: UUID): CommunitySummary {
        requireValidName(name)
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
    override fun update(
        communityId: UUID,
        requesterId: UUID,
        name: String,
        description: String?
    ): CommunitySummary {
        val community = communityRepository.findById(communityId)
            ?: throw BusinessException(ErrorCode.COMMUNITY_NOT_FOUND)

        requireAdminOrOwner(communityId, requesterId)
        requireValidName(name)

        val saved = communityRepository.update(
            community.copy(name = name, description = description, updatedAt = Instant.now())
        )
        log.info("Community updated: id={}, by={}", communityId, requesterId)

        // Re-read the counts rather than assume: the caller renders this straight back into the
        // list row it came from, and a rename must not reset the row's group and member numbers.
        return CommunitySummary(
            community = saved,
            groupCount = communityRepository.countGroupsByCommunityIds(listOf(communityId))[communityId] ?: 0,
            memberCount = communityRepository.countMembersByCommunityIds(listOf(communityId))[communityId] ?: 0
        )
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
    override fun delete(communityId: UUID, requesterId: UUID) {
        communityRepository.findById(communityId)
            ?: throw BusinessException(ErrorCode.COMMUNITY_NOT_FOUND)

        // Owner only, deliberately stricter than update/addGroup's admin-or-owner: this is
        // irreversible, so an admin acting up rather than an owner acting down is not enough.
        requireOwner(communityId, requesterId)

        // The FK cascade on community_members/community_groups (V16) does the unlinking; this
        // adapter call never touches conversationRepository, so no group's messages or members are
        // at risk (see CommunityRepository.delete and the persistence adapter for the schema note).
        communityRepository.delete(communityId)
        log.info("Community deleted: id={}, by={}", communityId, requesterId)
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
    override fun listMembers(communityId: UUID, requesterId: UUID): List<CommunityMemberSummary> {
        communityRepository.findById(communityId)
            ?: throw BusinessException(ErrorCode.COMMUNITY_NOT_FOUND)
        requireMember(communityId, requesterId)

        val members = communityRepository.findMembersByCommunityId(communityId)
        // One directory lookup for the whole list, not one per row.
        val displayInfo = userDirectoryPort.findDisplayInfo(members.map { it.userId })

        return members
            .sortedWith(compareBy({ it.role.ordinal }, { it.joinedAt }))
            .map { member ->
                val info = displayInfo[member.userId]
                CommunityMemberSummary(
                    userId = member.userId,
                    displayName = info?.displayName,
                    avatarUrl = info?.avatarUrl,
                    role = member.role,
                    joinedAt = member.joinedAt
                )
            }
    }

    @Transactional(readOnly = true)
    override fun listAddableUsers(communityId: UUID, requesterId: UUID): List<CommunityMemberCandidate> {
        communityRepository.findById(communityId)
            ?: throw BusinessException(ErrorCode.COMMUNITY_NOT_FOUND)
        requireAdminOrOwner(communityId, requesterId)

        val conversationIds = communityRepository.findGroupsByCommunityId(communityId).map { it.conversationId }
        if (conversationIds.isEmpty()) return emptyList()

        val alreadyMembers = communityRepository.findMembersByCommunityId(communityId).map { it.userId }.toSet()
        // Same rule addMember enforces, asked in the other direction: everyone in the community's
        // groups, minus everyone already in the community.
        val candidateIds = conversationRepository.findMembersByConversationIds(conversationIds)
            .values
            .flatten()
            .map { it.userId }
            .toSet() - alreadyMembers
        if (candidateIds.isEmpty()) return emptyList()

        val displayInfo = userDirectoryPort.findDisplayInfo(candidateIds)
        return candidateIds
            .map { userId ->
                val info = displayInfo[userId]
                CommunityMemberCandidate(
                    userId = userId,
                    displayName = info?.displayName,
                    avatarUrl = info?.avatarUrl
                )
            }
            // A set has no order, so without this the picker reshuffles on every open.
            .sortedWith(compareBy({ it.displayName == null }, { it.displayName }, { it.userId }))
    }

    @Transactional
    override fun leave(communityId: UUID, userId: UUID) {
        communityRepository.findById(communityId)
            ?: throw BusinessException(ErrorCode.COMMUNITY_NOT_FOUND)

        val member = requireMember(communityId, userId)
        val others = communityRepository.findMembersByCommunityId(communityId)
            .filter { it.userId != userId }

        if (others.isEmpty()) {
            // Removing the only member would leave rows nothing can reach: there is no discovery,
            // no invite (#387) and no delete endpoint. Refuse instead of orphaning the community.
            throw BusinessException(ErrorCode.COMMUNITY_LAST_MEMBER_CANNOT_LEAVE)
        }

        if (member.role == MemberRole.OWNER && others.none { it.role == MemberRole.OWNER }) {
            // Same succession as leaving a group: the longest-standing admin, else the
            // longest-standing member. A community with no owner can never be renamed or gain a
            // group again, so somebody must inherit before this row goes.
            val bySeniority = others.sortedBy { it.joinedAt }
            val successor = bySeniority.firstOrNull { it.role == MemberRole.ADMIN } ?: bySeniority.first()
            communityRepository.saveMember(successor.copy(role = MemberRole.OWNER))
            log.info("Community ownership transferred: community={}, to={}", communityId, successor.userId)
        }

        communityRepository.removeMember(communityId, userId)
        log.info("Member left community: community={}, user={}", communityId, userId)
    }

    @Transactional(readOnly = true)
    override fun getDetails(communityId: UUID, userId: UUID): CommunityDetails {
        val community = communityRepository.findById(communityId)
            ?: throw BusinessException(ErrorCode.COMMUNITY_NOT_FOUND)

        // Members only. There is no discovery, search or invite path in this app, so a community is
        // only ever opened from the caller's own list or straight after creating it — nothing
        // legitimate reads a community the caller does not belong to, and reading one exposes every
        // linked conversation's name, avatar and size.
        val membership = requireMember(communityId, userId)

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

    private fun requireMember(communityId: UUID, userId: UUID): CommunityMember =
        communityRepository.findMember(communityId, userId)
            ?: throw BusinessException(ErrorCode.COMMUNITY_PERMISSION_DENIED)

    private fun requireAdminOrOwner(communityId: UUID, userId: UUID): CommunityMember {
        val member = requireMember(communityId, userId)
        if (!member.administers()) {
            throw BusinessException(ErrorCode.COMMUNITY_PERMISSION_DENIED)
        }
        return member
    }

    private fun requireOwner(communityId: UUID, userId: UUID): CommunityMember {
        val member = requireMember(communityId, userId)
        if (member.role != MemberRole.OWNER) {
            throw BusinessException(ErrorCode.COMMUNITY_PERMISSION_DENIED)
        }
        return member
    }

    private fun requireValidName(name: String) {
        if (!ValidationRules.isValidCommunityName(name)) {
            throw BusinessException(ErrorCode.COMMUNITY_INVALID_NAME)
        }
    }
}
