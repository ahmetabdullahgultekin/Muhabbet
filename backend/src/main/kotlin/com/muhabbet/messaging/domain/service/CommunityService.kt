package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Community
import com.muhabbet.messaging.domain.model.CommunityGroup
import com.muhabbet.messaging.domain.model.CommunityMember
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.`in`.CommunityDetails
import com.muhabbet.messaging.domain.port.`in`.CommunityGroupSummary
import com.muhabbet.messaging.domain.port.`in`.CommunityMemberCandidate
import com.muhabbet.messaging.domain.port.`in`.CommunityMemberSummary
import com.muhabbet.messaging.domain.port.`in`.CommunitySummary
import com.muhabbet.messaging.domain.port.`in`.ManageCommunityMembershipUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageCommunityUseCase
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
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
    private val userDirectoryPort: UserDirectoryPort,
    private val blockPolicy: BlockPolicyPort
) : ManageCommunityUseCase, ManageCommunityMembershipUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun create(name: String, description: String?, creatorId: UUID): CommunitySummary {
        requireValidName(name)
        // Validity first, availability second: a blank name is malformed (400), a taken one is a
        // conflict (409), and answering "already exists" to an empty field would be nonsense.
        requireNameAvailable(creatorId, name, keeping = null)
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

        // Every community carries an announcement channel from the moment it exists (#584) — the
        // one place a member can always speak, or read from admins, rather than a community being
        // nothing but a row in a list. The creator was just saved above, so the bulk-enrol inside
        // ensureAnnouncementChannel picks them up and seats them as its owner.
        val channelId = ensureAnnouncementChannel(saved)

        log.info("Community created: id={}, name={}, creator={}", saved.id, name, creatorId)
        // A community starts with no groups and exactly one member: the creator just added above.
        return CommunitySummary(
            community = saved.copy(announcementGroupId = channelId),
            groupCount = 0,
            memberCount = 1
        )
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
        // Asked about the community's creator, not the caller: an admin may rename a community
        // somebody else created, and the index is on (created_by, name). Asking about the caller's
        // own names would let the rename past here and fail at the database instead.
        requireNameAvailable(community.createdBy, name, keeping = communityId)

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
        val community = communityRepository.findById(communityId)
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
        val channelId = ensureAnnouncementChannel(community)
        conversationRepository.saveMember(
            ConversationMember(conversationId = channelId, userId = userId, role = MemberRole.MEMBER)
        )

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

        // The same predicate addMember enforces, asked in the batched direction (#294). The picker
        // must not offer a person the caller cannot add: leaving them in makes the row a dead
        // control, and — tapped repeatedly across candidates — a way to learn who blocked you by
        // elimination. Filtered ahead of the directory lookup, so a blocker's name and face are
        // never read either.
        val addable = candidateIds - blockPolicy.findBlockedBy(requesterId, candidateIds)
        if (addable.isEmpty()) return emptyList()

        val displayInfo = userDirectoryPort.findDisplayInfo(addable)
        return addable
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
        val community = communityRepository.findById(communityId)
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

    // Deliberately not `readOnly = true`: ensureAnnouncementChannel below writes for any community
    // that predates #584, and a read-only Postgres transaction refuses an INSERT outright rather
    // than silently no-op-ing it.
    @Transactional
    override fun getDetails(communityId: UUID, userId: UUID): CommunityDetails {
        val community = communityRepository.findById(communityId)
            ?: throw BusinessException(ErrorCode.COMMUNITY_NOT_FOUND)

        // Members only. There is no discovery, search or invite path in this app, so a community is
        // only ever opened from the caller's own list or straight after creating it — nothing
        // legitimate reads a community the caller does not belong to, and reading one exposes every
        // linked conversation's name, avatar and size.
        val membership = requireMember(communityId, userId)

        // Self-healing backfill for the communities that existed before #584: the eight rows already
        // in production, each with one member and nowhere to speak. No SQL migration reaches into
        // `conversations` to fabricate a channel for them; the first read after this ships does it
        // instead, with the same code path a brand-new community goes through in `create`.
        val channelId = ensureAnnouncementChannel(community)

        val groups = communityRepository.findGroupsByCommunityId(communityId)
        val conversationIds = groups.map { it.conversationId }
        // Two batched lookups instead of two queries per group.
        val conversations = conversationRepository.findConversationsByIds(conversationIds).associateBy { it.id }
        val groupMemberCounts = conversationRepository.countMembersByConversationIds(conversationIds)

        return CommunityDetails(
            // `community` alone would still show the pre-backfill null here when ensureAnnouncementChannel
            // just created the channel above — copy it in so this field and the dedicated
            // announcementGroupId below never disagree.
            community = community.copy(announcementGroupId = channelId),
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
            myRole = membership.role,
            announcementGroupId = channelId
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

    /**
     * Returns [community]'s announcement channel, creating it first if this is the first time
     * anything has asked (#584). The channel is a GROUP conversation, `announcementOnly = true`, that
     * carries every current community member — owners and admins seated as conversation ADMIN (one
     * of them, the community's own OWNER, as conversation OWNER too), everyone else as a plain
     * MEMBER. `MessageService.sendMessage` already refuses a plain MEMBER on an `announcementOnly`
     * conversation (`MSG_ANNOUNCEMENT_ONLY`), and a message that does get through fans out over the
     * same `MessageBroadcaster` path every other conversation uses — nothing new was needed on
     * either side of the send path for this to work.
     *
     * Idempotent by the `announcementGroupId` check, which is also what makes this the backfill for
     * communities created before this existed: called from `create` (freshly, for the member just
     * added) and from `getDetails`/`addMember` (lazily, for a community that predates it), never a
     * SQL migration reaching into `conversations`.
     *
     * Not safe against two concurrent first-calls on the same community racing each other into two
     * channels — there is no row lock here. Accepted for now: this app has no concurrent-access
     * volume that makes it likely, and the loser's channel is merely orphaned, not destructive.
     */
    private fun ensureAnnouncementChannel(community: Community): UUID {
        community.announcementGroupId?.let { return it }

        val members = communityRepository.findMembersByCommunityId(community.id)
        val channel = conversationRepository.save(
            Conversation(
                type = ConversationType.GROUP,
                name = community.name.take(ValidationRules.GROUP_NAME_MAX),
                createdBy = community.createdBy,
                announcementOnly = true
            )
        )
        members.forEach { member ->
            conversationRepository.saveMember(
                ConversationMember(
                    conversationId = channel.id,
                    userId = member.userId,
                    role = if (member.administers()) MemberRole.ADMIN else MemberRole.MEMBER
                )
            )
        }
        // Exactly one OWNER, matching what GroupService's own role/succession logic expects of any
        // GROUP conversation — administers() alone would leave the community's OWNER seated as only
        // an ADMIN of their own channel.
        members.firstOrNull { it.role == MemberRole.OWNER }?.let { owner ->
            conversationRepository.updateMemberRole(channel.id, owner.userId, MemberRole.OWNER)
        }

        communityRepository.update(community.copy(announcementGroupId = channel.id))
        log.info("Announcement channel created for community: community={}, channel={}", community.id, channel.id)
        return channel.id
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

    /**
     * Refuses [name] if [creatorId] already has a community under it (#446) — three rows called
     * "Muhabbet" in one list are indistinguishable, so neither the owner nor the app can tell which
     * one a tap is about.
     *
     * [keeping] is the community being renamed, excluded from the match so that saving a community
     * under the name it already has is not a conflict with itself; `null` when creating, where
     * there is no such row yet.
     *
     * The comparison is the database's, not this service's: [CommunityRepository.findByCreatorAndName]
     * applies the same fold as the unique index. This check exists to produce a clean 409 with a
     * usable message, not to be the guarantee — two concurrent creates can both pass it, and the
     * index plus `CommunityPersistenceAdapter`'s translation of the violation are what actually hold
     * the invariant.
     */
    private fun requireNameAvailable(creatorId: UUID, name: String, keeping: UUID?) {
        val existing = communityRepository.findByCreatorAndName(creatorId, name) ?: return
        if (existing.id != keeping) {
            throw BusinessException(ErrorCode.COMMUNITY_NAME_ALREADY_EXISTS)
        }
    }
}
