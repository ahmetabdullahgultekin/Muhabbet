package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.Community
import com.muhabbet.messaging.domain.model.CommunityGroup
import com.muhabbet.messaging.domain.model.CommunityMember
import com.muhabbet.messaging.domain.model.MemberRole
import java.time.Instant
import java.util.UUID

/**
 * A community plus the two counts every list row shows. The counts are not on [Community]
 * because they are aggregates over other tables, not state the aggregate root owns.
 */
data class CommunitySummary(
    val community: Community,
    val groupCount: Int,
    val memberCount: Int
)

/**
 * One group inside a community, carrying the conversation's own display fields so the caller
 * does not have to fetch each conversation separately.
 */
data class CommunityGroupSummary(
    val conversationId: UUID,
    val name: String?,
    val avatarUrl: String?,
    val memberCount: Int
)

/**
 * @param myRole the requester's role in this community. Never null: [ManageCommunityUseCase.getDetails]
 * refuses non-members, so a details object only ever describes a community the requester belongs to.
 * @param announcementGroupId the community's announcement channel — a GROUP conversation every
 * member is enrolled in, where only admins and owners may post (#584). Practically never null: the
 * service creates one the moment a community is created, and backfills it lazily on read for any
 * community that predates that (see `CommunityService.ensureAnnouncementChannel`). Left nullable on
 * the wire anyway, the same reasoning as [myRole] below applied to a field that used to not exist at
 * all: a client that has not shipped the "open announcements" affordance yet must still decode this
 * response.
 */
data class CommunityDetails(
    val community: Community,
    val groups: List<CommunityGroupSummary>,
    val memberCount: Int,
    val myRole: MemberRole,
    val announcementGroupId: UUID? = null
)

/**
 * One member of a community, with the user's own display fields resolved so the caller does not
 * have to look each person up separately.
 */
data class CommunityMemberSummary(
    val userId: UUID,
    val displayName: String?,
    val avatarUrl: String?,
    val role: MemberRole,
    val joinedAt: Instant
)

/**
 * Someone an admin may enrol right now: a member of one of the community's groups who is not yet a
 * member of the community itself.
 *
 * Exists so the picker and [ManageCommunityMembershipUseCase.addMember] answer the same question.
 * A picker that listed all contacts would offer people the add then refuses with
 * `COMMUNITY_MEMBER_NOT_IN_ANY_GROUP`, which reads to the user as a broken button.
 */
data class CommunityMemberCandidate(
    val userId: UUID,
    val displayName: String?,
    val avatarUrl: String?
)

interface ManageCommunityUseCase {
    fun create(name: String, description: String?, creatorId: UUID): CommunitySummary

    /**
     * Renames a community and replaces its description. Admins and owners only.
     *
     * @param description the new description, or `null` to clear it — this is a replace, not a
     * merge, so the caller sends the whole thing.
     * @throws com.muhabbet.shared.exception.BusinessException `COMMUNITY_NOT_FOUND` when no such
     * community exists, `COMMUNITY_PERMISSION_DENIED` when the caller does not run it,
     * `COMMUNITY_INVALID_NAME` when the name is blank or longer than the column allows.
     */
    fun update(communityId: UUID, requesterId: UUID, name: String, description: String?): CommunitySummary

    /**
     * Links an existing GROUP conversation to a community. The caller must be an admin or owner of
     * the community **and** a member of the conversation — the conversation id comes from the
     * caller, so it is authorization, not a lookup key.
     *
     * @throws com.muhabbet.shared.exception.BusinessException `COMMUNITY_PERMISSION_DENIED` when the
     * caller does not run the community, `GROUP_NOT_MEMBER` when the caller is not in the
     * conversation, `COMMUNITY_NOT_A_GROUP` when the conversation is a direct chat or a channel.
     */
    fun addGroup(communityId: UUID, conversationId: UUID, userId: UUID): CommunityGroup

    fun removeGroup(communityId: UUID, conversationId: UUID, userId: UUID)

    /**
     * Deletes the community outright. Owner only — unlike [update] and [addGroup], an admin cannot
     * do this: it is irreversible and cannot be undone from inside the app the way a rename or an
     * unlink can.
     *
     * Cascades to `community_members` and `community_groups` (the community's own membership and
     * group-link rows). It does **not** delete the linked conversations: a group that outlives its
     * community must keep its messages and members, so only the link is removed, never the group.
     *
     * @throws com.muhabbet.shared.exception.BusinessException `COMMUNITY_NOT_FOUND` when no such
     * community exists, `COMMUNITY_PERMISSION_DENIED` when the caller is not the owner.
     */
    fun delete(communityId: UUID, requesterId: UUID)

    /**
     * Reads a community. Members only: this returns the community's groups, and with them the name,
     * avatar and member count of each linked conversation.
     *
     * @throws com.muhabbet.shared.exception.BusinessException `COMMUNITY_NOT_FOUND` when no such
     * community exists, `COMMUNITY_PERMISSION_DENIED` when the requester is not a member.
     */
    fun getDetails(communityId: UUID, userId: UUID): CommunityDetails

    fun listForUser(userId: UUID): List<CommunitySummary>
}

/**
 * Who is in a community, and how that changes.
 *
 * Split from [ManageCommunityUseCase] rather than added to it: the two are used by different
 * screens and, with the members work, a single interface would have reached ten methods — the exact
 * shape CLAUDE.md's interface-segregation rule names. One service still implements both, so no
 * behaviour is spread across classes; only the contracts a caller must depend on are narrowed.
 */
interface ManageCommunityMembershipUseCase {

    /**
     * Every member of the community, with display names resolved. Members only, for the same reason
     * [ManageCommunityUseCase.getDetails] is: the answer is a list of who the caller's neighbours
     * are, which nobody outside should be able to enumerate.
     *
     * @throws com.muhabbet.shared.exception.BusinessException `COMMUNITY_NOT_FOUND` when no such
     * community exists, `COMMUNITY_PERMISSION_DENIED` when the requester is not a member.
     */
    fun listMembers(communityId: UUID, requesterId: UUID): List<CommunityMemberSummary>

    /**
     * The people [addMember] would currently accept: members of the community's own groups who are
     * not yet community members. Admins and owners only — it discloses the membership of every
     * linked group, and only an admin can act on it.
     */
    fun listAddableUsers(communityId: UUID, requesterId: UUID): List<CommunityMemberCandidate>

    /**
     * Enrols [userId] in the community. Until an invite flow exists (#387), the target must already
     * be a member of one of the community's groups — see the note in the implementation.
     */
    fun addMember(communityId: UUID, userId: UUID, requesterId: UUID): CommunityMember

    /**
     * Removes the caller from the community.
     *
     * The last owner does not strand the community: ownership passes to the longest-standing
     * remaining admin, or failing that the longest-standing member, exactly as leaving a group
     * does. A sole member is refused rather than allowed to leave an unreachable community behind —
     * [ManageCommunityUseCase.delete] is the deliberate way to remove one, not the last leave.
     *
     * @throws com.muhabbet.shared.exception.BusinessException `COMMUNITY_NOT_FOUND` when no such
     * community exists, `COMMUNITY_PERMISSION_DENIED` when the caller is not a member,
     * `COMMUNITY_LAST_MEMBER_CANNOT_LEAVE` when the caller is the only member.
     */
    fun leave(communityId: UUID, userId: UUID)
}
