package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.CommunityInviteLink
import java.time.Instant
import java.util.UUID

/**
 * Everything a **non-member** learns from holding an invite token, and nothing else.
 *
 * This is the one shape in the community vertical deliberately readable by someone outside the
 * community, so its field list is a security decision rather than a convenience. #375 was filed
 * because `getDetails` leaked the group list to any authenticated caller; #416 asks the question
 * directly — "if some are public, what is disclosed to a non-member?" — and this type is the
 * narrowest answer that still lets a person decide whether to accept: what the community is called,
 * how big it is, and who invited them.
 *
 * Explicitly **not** here: the group list, the member list, the community's description-by-id, or
 * anything that would let a token holder enumerate the graph. And the disclosure is keyed on
 * possession of a 32-byte secret, not on being logged in — this is not a directory.
 *
 * @param alreadyMember true when the caller is already in this community. The screen shows "open"
 * instead of "join" rather than offering an accept that would fail, and no `use_count` is spent.
 */
data class CommunityInvitePreview(
    val communityId: UUID,
    val name: String,
    val avatarUrl: String?,
    val memberCount: Int,
    val inviterDisplayName: String?,
    val alreadyMember: Boolean
)

/**
 * Invite links: the only path by which a community can gain a member who was not already inside one
 * of its groups (#387, #416).
 *
 * Split from [ManageCommunityUseCase] and [ManageCommunityMembershipUseCase] for the reason the
 * latter two were split from each other — a different screen, a different reason to change, and a
 * third block of methods on either would push both past the size CLAUDE.md's interface-segregation
 * rule tolerates.
 */
interface ManageCommunityInviteUseCase {

    /**
     * Mints a link. **Admins and owners only** — a plain member cannot decide who may join.
     *
     * @param maxUses null for unlimited, otherwise how many people the link may admit.
     * @param expiresAt null for no expiry.
     * @throws com.muhabbet.shared.exception.BusinessException `COMMUNITY_NOT_FOUND`,
     * `COMMUNITY_PERMISSION_DENIED` when the caller does not administer the community,
     * `COMMUNITY_INVITE_LIMIT_REACHED` when too many active links already exist,
     * `COMMUNITY_INVITE_INVALID_MAX_USES` when `maxUses` is zero or negative,
     * `COMMUNITY_INVITE_INVALID_EXPIRY` when `expiresAt` is already in the past.
     */
    fun createLink(
        communityId: UUID,
        requesterId: UUID,
        maxUses: Int?,
        expiresAt: Instant?
    ): CommunityInviteLink

    /**
     * Every active link for a community. **Admins and owners only**: a token is a bearer credential,
     * so listing them to a plain member would hand every member the power to admit anyone.
     */
    fun listLinks(communityId: UUID, requesterId: UUID): List<CommunityInviteLink>

    /**
     * Revokes a link. **Admins and owners of the link's own community only** — the id comes from the
     * caller, so the community is read from the link and the caller checked against *that*, never
     * against a community id the caller supplied.
     */
    fun revokeLink(communityId: UUID, linkId: UUID, requesterId: UUID)

    /**
     * What the token holder is being offered. **Any authenticated caller who holds the token** —
     * that is the whole authorisation, and why [CommunityInvitePreview] discloses as little as it
     * does. Does not join anything and does not spend a use.
     *
     * @throws com.muhabbet.shared.exception.BusinessException `INVITE_LINK_NOT_FOUND` when the token
     * is unknown or revoked, `INVITE_LINK_EXPIRED`, `INVITE_LINK_MAX_USES`.
     */
    fun preview(token: String, requesterId: UUID): CommunityInvitePreview

    /**
     * The accept step — the action by the joining person that #387 says must exist.
     *
     * Writes `community_members` and enrols the new member in the community's announcement channel
     * (#584), so that joining lands somewhere with something to read rather than in an empty tab.
     * Does **not** add them to the community's groups: in this model community membership is the
     * container, and each group is still joined on its own.
     *
     * @return the community joined, so the caller can navigate straight into it.
     * @throws com.muhabbet.shared.exception.BusinessException `INVITE_LINK_NOT_FOUND`,
     * `INVITE_LINK_EXPIRED`, `INVITE_LINK_MAX_USES`, `GROUP_ALREADY_MEMBER` when the caller is
     * already in the community.
     */
    fun accept(token: String, userId: UUID): CommunitySummary
}
