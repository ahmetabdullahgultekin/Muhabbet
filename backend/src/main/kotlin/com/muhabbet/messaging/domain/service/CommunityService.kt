package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Community
import com.muhabbet.messaging.domain.model.CommunityMember
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.`in`.CommunityDetails
import com.muhabbet.messaging.domain.port.`in`.CommunityGroupSummary
import com.muhabbet.messaging.domain.port.`in`.CommunitySummary
import com.muhabbet.messaging.domain.port.`in`.ManageCommunityUseCase
import com.muhabbet.messaging.domain.port.out.CommunityRepository
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.transaction.TransactionRunner
import com.muhabbet.shared.validation.ValidationRules
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * A community's lifecycle and what reading one returns (#894). Linking groups is
 * [CommunityGroupService]; who is in it is [CommunityMembershipService].
 */
class CommunityService(
    private val communityRepository: CommunityRepository,
    private val conversationRepository: ConversationRepository,
    private val transactions: TransactionRunner
) : ManageCommunityUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val access = CommunityAccess(communityRepository)
    private val announcementChannel = CommunityAnnouncementChannel(communityRepository, conversationRepository)

    override fun create(name: String, description: String?, creatorId: UUID): CommunitySummary = transactions.inTransaction {
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
        val channelId = announcementChannel.ensure(saved)

        log.info("Community created: id={}, name={}, creator={}", saved.id, name, creatorId)
        // A community starts with no groups and exactly one member: the creator just added above.
        return@inTransaction CommunitySummary(
            community = saved.copy(announcementGroupId = channelId),
            groupCount = 0,
            memberCount = 1
        )
    }

    override fun update(
        communityId: UUID,
        requesterId: UUID,
        name: String,
        description: String?
    ): CommunitySummary = transactions.inTransaction {
        val community = access.requireCommunity(communityId)

        access.requireAdminOrOwner(communityId, requesterId)
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
        return@inTransaction CommunitySummary(
            community = saved,
            groupCount = communityRepository.countGroupsByCommunityIds(listOf(communityId))[communityId] ?: 0,
            memberCount = communityRepository.countMembersByCommunityIds(listOf(communityId))[communityId] ?: 0
        )
    }

    override fun delete(communityId: UUID, requesterId: UUID) {
        transactions.inTransaction<Unit> {
            access.requireCommunity(communityId)

            // Owner only, deliberately stricter than update/addGroup's admin-or-owner: this is
            // irreversible, so an admin acting up rather than an owner acting down is not enough.
            access.requireOwner(communityId, requesterId)

            // The FK cascade on community_members/community_groups (V16) does the unlinking; this
            // adapter call never touches conversationRepository, so no group's messages or members are
            // at risk (see CommunityRepository.delete and the persistence adapter for the schema note).
            communityRepository.delete(communityId)
            log.info("Community deleted: id={}, by={}", communityId, requesterId)
        }
    }

    // Deliberately not `readOnly = true`: ensureAnnouncementChannel below writes for any community
    // that predates #584, and a read-only Postgres transaction refuses an INSERT outright rather
    // than silently no-op-ing it.
    override fun getDetails(communityId: UUID, userId: UUID): CommunityDetails = transactions.inTransaction {
        val community = access.requireCommunity(communityId)

        // Members only. There is no discovery, search or invite path in this app, so a community is
        // only ever opened from the caller's own list or straight after creating it — nothing
        // legitimate reads a community the caller does not belong to, and reading one exposes every
        // linked conversation's name, avatar and size.
        val membership = access.requireMember(communityId, userId)

        // Self-healing backfill for the communities that existed before #584: the eight rows already
        // in production, each with one member and nowhere to speak. No SQL migration reaches into
        // `conversations` to fabricate a channel for them; the first read after this ships does it
        // instead, with the same code path a brand-new community goes through in `create`.
        val channelId = announcementChannel.ensure(community)

        val groups = communityRepository.findGroupsByCommunityId(communityId)
        val conversationIds = groups.map { it.conversationId }
        // Two batched lookups instead of two queries per group.
        val conversations = conversationRepository.findConversationsByIds(conversationIds).associateBy { it.id }
        val groupMemberCounts = conversationRepository.countMembersByConversationIds(conversationIds)

        return@inTransaction CommunityDetails(
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

    override fun listForUser(userId: UUID): List<CommunitySummary> = transactions.inReadOnlyTransaction {
        val communities = communityRepository.findCommunitiesByUserId(userId)
        if (communities.isEmpty()) return@inReadOnlyTransaction emptyList()

        // Batched so the list costs three queries regardless of how many communities come back.
        val communityIds = communities.map { it.id }
        val groupCounts = communityRepository.countGroupsByCommunityIds(communityIds)
        val memberCounts = communityRepository.countMembersByCommunityIds(communityIds)

        return@inReadOnlyTransaction communities.map { community ->
            CommunitySummary(
                community = community,
                groupCount = groupCounts[community.id] ?: 0,
                memberCount = memberCounts[community.id] ?: 0
            )
        }
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
