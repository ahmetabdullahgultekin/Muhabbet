package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.CommunityMember
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.`in`.CommunityMemberCandidate
import com.muhabbet.messaging.domain.port.`in`.CommunityMemberSummary
import com.muhabbet.messaging.domain.port.`in`.ManageCommunityMembershipUseCase
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.CommunityRepository
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.transaction.TransactionRunner
import org.slf4j.LoggerFactory
import java.util.UUID

/** Who is in a community, and how that changes (#894). */
class CommunityMembershipService(
    private val communityRepository: CommunityRepository,
    private val conversationRepository: ConversationRepository,
    private val userDirectoryPort: UserDirectoryPort,
    private val blockPolicy: BlockPolicyPort,
    private val transactions: TransactionRunner
) : ManageCommunityMembershipUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val access = CommunityAccess(communityRepository)
    private val announcementChannel = CommunityAnnouncementChannel(communityRepository, conversationRepository)

    override fun addMember(communityId: UUID, userId: UUID, requesterId: UUID): CommunityMember = transactions.inTransaction {
        val community = access.requireCommunity(communityId)

        access.requireAdminOrOwner(communityId, requesterId)

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

        // Someone who blocked you does not get pulled into a room with you (#294) — the same rule
        // GroupService.addMembers enforces, and for the same reason: enrolment ends in the
        // announcement channel below, which is a GROUP conversation this caller can post to.
        // Reaching a person through a room you added them to is the reach the send path already
        // refuses.
        //
        // The group-membership rule above narrows the exposure without closing it. It permits
        // enrolling anyone already in one of the community's groups, and a shared group is exactly
        // what two people still have after one of them blocks the other.
        //
        // It reuses COMMUNITY_MEMBER_NOT_IN_ANY_GROUP, the code raised immediately above, rather
        // than getting one of its own. A distinct code would be a reliable one-bit oracle: make a
        // throwaway community, add the target, read the code, and you know they blocked you.
        // Sharing the code with the ordinary "not addable" case is what keeps the answer ambiguous.
        // Deliberately after the permission and membership checks, so a caller who fails those is
        // refused without the block table being consulted at all.
        if (blockPolicy.hasBlocked(userId, requesterId)) {
            log.info("Community add refused, the invitee has blocked the requester: community={}, requester={}", communityId, requesterId)
            throw BusinessException(ErrorCode.COMMUNITY_MEMBER_NOT_IN_ANY_GROUP)
        }

        val member = CommunityMember(communityId = communityId, userId = userId, role = MemberRole.MEMBER)
        val saved = communityRepository.saveMember(member)

        // A member added later is a member the announcement channel must also carry (#584, the exact
        // check the issue named) — `saveMember` upserts, so this is safe whether the channel already
        // existed or was only just created above (in which case the bulk-enrol inside
        // ensureAnnouncementChannel already saw this user, since it re-reads membership after the
        // line above).
        val channelId = announcementChannel.ensure(community)
        conversationRepository.saveMember(
            ConversationMember(conversationId = channelId, userId = userId, role = MemberRole.MEMBER)
        )

        log.info("Member added to community: community={}, user={}", communityId, userId)
        return@inTransaction saved
    }

    override fun listMembers(communityId: UUID, requesterId: UUID): List<CommunityMemberSummary> = transactions.inReadOnlyTransaction {
        access.requireCommunity(communityId)
        access.requireMember(communityId, requesterId)

        val members = communityRepository.findMembersByCommunityId(communityId)
        // One directory lookup for the whole list, not one per row.
        val displayInfo = userDirectoryPort.findDisplayInfo(members.map { it.userId })

        return@inReadOnlyTransaction members
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

    override fun listAddableUsers(communityId: UUID, requesterId: UUID): List<CommunityMemberCandidate> = transactions.inReadOnlyTransaction {
        access.requireCommunity(communityId)
        access.requireAdminOrOwner(communityId, requesterId)

        val conversationIds = communityRepository.findGroupsByCommunityId(communityId).map { it.conversationId }
        if (conversationIds.isEmpty()) return@inReadOnlyTransaction emptyList()

        val alreadyMembers = communityRepository.findMembersByCommunityId(communityId).map { it.userId }.toSet()
        // Same rule addMember enforces, asked in the other direction: everyone in the community's
        // groups, minus everyone already in the community.
        val candidateIds = conversationRepository.findMembersByConversationIds(conversationIds)
            .values
            .flatten()
            .map { it.userId }
            .toSet() - alreadyMembers
        if (candidateIds.isEmpty()) return@inReadOnlyTransaction emptyList()

        // The same predicate addMember enforces, asked in the batched direction (#294). The picker
        // must not offer a person the caller cannot add: leaving them in makes the row a dead
        // control, and — tapped repeatedly across candidates — a way to learn who blocked you by
        // elimination. Filtered ahead of the directory lookup, so a blocker's name and face are
        // never read either.
        val addable = candidateIds - blockPolicy.findBlockedBy(requesterId, candidateIds)
        if (addable.isEmpty()) return@inReadOnlyTransaction emptyList()

        val displayInfo = userDirectoryPort.findDisplayInfo(addable)
        return@inReadOnlyTransaction addable
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

    override fun leave(communityId: UUID, userId: UUID) {
        transactions.inTransaction<Unit> {
            val community = access.requireCommunity(communityId)

            val member = access.requireMember(communityId, userId)
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

                // The announcement channel must always have an owner too, for the same reason a group
                // does — GroupService's own leave/role flows assume exactly one. Promoted before the
                // leaving owner is removed from the channel below, never left ownerless in between.
                community.announcementGroupId?.let { channelId ->
                    conversationRepository.updateMemberRole(channelId, successor.userId, MemberRole.OWNER)
                }
            }

            communityRepository.removeMember(communityId, userId)

            // Leaving the community means leaving its announcement channel too — otherwise a former
            // member keeps reading (and, if they were an admin, posting to) a channel their community
            // membership no longer explains. `null` here only for a community nobody has read or changed
            // since #584 shipped; ensureAnnouncementChannel backfills it on the next getDetails/addMember.
            community.announcementGroupId?.let { channelId ->
                conversationRepository.removeMember(channelId, userId)
            }

            log.info("Member left community: community={}, user={}", communityId, userId)
        }
    }
}
