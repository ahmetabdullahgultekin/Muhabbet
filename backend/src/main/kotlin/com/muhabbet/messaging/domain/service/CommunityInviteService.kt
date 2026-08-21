package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.CommunityInviteLink
import com.muhabbet.messaging.domain.model.CommunityMember
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.`in`.CommunityInvitePreview
import com.muhabbet.messaging.domain.port.`in`.CommunitySummary
import com.muhabbet.messaging.domain.port.`in`.ManageCommunityInviteUseCase
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.CommunityInviteLinkRepository
import com.muhabbet.messaging.domain.port.out.CommunityRepository
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Invite links for communities — the path by which a community can gain a member who was not
 * already inside one of its groups (#387, #416).
 *
 * Before this, it could not. `CommunityService.addMember` refuses anyone outside the community's own
 * groups (the restriction #375 shipped in place of an invite system), so a community with no groups
 * had an empty candidate set and could never reach a second member. Production shows the result
 * exactly: eight communities, one member each, zero groups (#407).
 *
 * The rule that makes this safe is the one #387 asks for: **an invite is an offer, not a
 * membership.** Nothing is written to `community_members` when a link is minted. The row appears in
 * [accept], on an action taken by the person joining. That is the whole difference between this and
 * the owner-side add that had to be restricted.
 *
 * Separate from [CommunityService] rather than a third interface on it: that class already
 * implements two use cases and sits near the size CLAUDE.md's "no God classes" rule warns about. The
 * two share what they must not disagree about — the announcement channel via
 * [CommunityAnnouncementChannel], and the authorisation guards via the extensions in
 * `CommunityAccess.kt` — and nothing else.
 */
open class CommunityInviteService(
    private val communityRepository: CommunityRepository,
    private val inviteLinkRepository: CommunityInviteLinkRepository,
    private val userDirectoryPort: UserDirectoryPort,
    private val blockPolicy: BlockPolicyPort,
    private val announcementChannel: CommunityAnnouncementChannel
) : ManageCommunityInviteUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /**
         * How many active links one community may hold at once.
         *
         * #387 asks for a per-user cap on invites for abuse reasons. For links the meaningful unit
         * is the community, not the user: a link is a bearer credential that keeps working, so an
         * unbounded set of them is an unbounded set of ways in that no admin can reasonably audit or
         * revoke. Ten is enough for "one per class / per building / per year" and small enough that
         * the revoke list stays a list a person can read.
         */
        const val MAX_ACTIVE_LINKS_PER_COMMUNITY = 10
    }

    @Transactional
    override fun createLink(
        communityId: UUID,
        requesterId: UUID,
        maxUses: Int?,
        expiresAt: Instant?
    ): CommunityInviteLink {
        communityRepository.findById(communityId)
            ?: throw BusinessException(ErrorCode.COMMUNITY_NOT_FOUND)

        // Admins and owners only: minting a link is deciding who may join, which is exactly the
        // authority a plain member does not have.
        communityRepository.requireAdminOrOwner(communityId, requesterId)

        if (maxUses != null && maxUses <= 0) {
            throw BusinessException(ErrorCode.COMMUNITY_INVITE_INVALID_MAX_USES)
        }
        // A link that is already expired is not a link, it is a support ticket. Refuse at the door
        // rather than store one that every later read has to explain away.
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw BusinessException(ErrorCode.COMMUNITY_INVITE_INVALID_EXPIRY)
        }
        if (inviteLinkRepository.countActiveByCommunityId(communityId) >= MAX_ACTIVE_LINKS_PER_COMMUNITY) {
            throw BusinessException(ErrorCode.COMMUNITY_INVITE_LIMIT_REACHED)
        }

        val saved = inviteLinkRepository.save(
            CommunityInviteLink(
                communityId = communityId,
                inviteToken = generateInviteToken(),
                createdBy = requesterId,
                maxUses = maxUses,
                expiresAt = expiresAt
            )
        )
        // The token is a bearer credential: never logged in full, here or anywhere.
        log.info(
            "Community invite link created: community={}, by={}, token={}…",
            communityId,
            requesterId,
            saved.inviteToken.take(8)
        )
        return saved
    }

    @Transactional(readOnly = true)
    override fun listLinks(communityId: UUID, requesterId: UUID): List<CommunityInviteLink> {
        communityRepository.findById(communityId)
            ?: throw BusinessException(ErrorCode.COMMUNITY_NOT_FOUND)
        // Admins and owners only. A member who could read this list could admit anyone to the
        // community, which is the authority the list is supposed to be gating.
        communityRepository.requireAdminOrOwner(communityId, requesterId)

        return inviteLinkRepository.findActiveByCommunityId(communityId)
    }

    @Transactional
    override fun revokeLink(communityId: UUID, linkId: UUID, requesterId: UUID) {
        val link = inviteLinkRepository.findById(linkId)
            ?: throw BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND)

        // Both ids come from the caller, so the one that decides authority is read from the stored
        // row, not from the path. Without this an admin of community A could revoke a link
        // belonging to community B by naming A in the URL and B's link id in it.
        if (link.communityId != communityId) {
            throw BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND)
        }
        communityRepository.requireAdminOrOwner(link.communityId, requesterId)

        inviteLinkRepository.deactivate(linkId)
        log.info("Community invite link revoked: community={}, link={}, by={}", communityId, linkId, requesterId)
    }

    @Transactional(readOnly = true)
    override fun preview(token: String, requesterId: UUID): CommunityInvitePreview {
        val link = requireUsableLink(token)
        val community = communityRepository.findById(link.communityId)
            ?: throw BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND)

        // Checked here rather than left for accept to throw, because a member re-opening a link
        // they were sent is an ordinary thing to do, not an error — the screen offers "open"
        // instead of a join that would fail.
        val alreadyMember = communityRepository.findMember(community.id, requesterId) != null

        // The same guard [accept] applies, asked one step earlier so the screen never offers a Join
        // button the accept would refuse. Skipped for a member: they are already inside, and the
        // guard is about being seated in a room, not about staying in one.
        //
        // Ahead of the directory lookup on purpose, so a blocked inviter's name and avatar are
        // never read, let alone returned. The same ordering listAddableMembers uses (#685).
        if (!alreadyMember) requireInviterNotBlockedBy(link, requesterId)

        // Everything below is chosen to be the least a person needs in order to decide whether to
        // accept. No group list, no member list — reading those still requires membership (#375).
        val inviterDisplayName = userDirectoryPort
            .findDisplayInfo(listOf(link.createdBy))[link.createdBy]
            ?.displayName

        return CommunityInvitePreview(
            communityId = community.id,
            name = community.name,
            avatarUrl = community.avatarUrl,
            memberCount = memberCountOf(community.id),
            inviterDisplayName = inviterDisplayName,
            alreadyMember = alreadyMember
        )
    }

    @Transactional
    override fun accept(token: String, userId: UUID): CommunitySummary {
        val link = requireUsableLink(token)
        val community = communityRepository.findById(link.communityId)
            ?: throw BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND)

        if (communityRepository.findMember(community.id, userId) != null) {
            throw BusinessException(ErrorCode.GROUP_ALREADY_MEMBER)
        }

        // Deliberately after the cheaper membership check, so someone who is already in is refused
        // for that reason without the block table being consulted at all — the ordering
        // CommunityService.addMember uses.
        requireInviterNotBlockedBy(link, userId)

        // The membership row, written here and nowhere earlier. This line is what #387 is about:
        // the person joining performed an action, and only then did they become a member.
        communityRepository.saveMember(
            CommunityMember(communityId = community.id, userId = userId, role = MemberRole.MEMBER)
        )

        // Joining has to land somewhere with something in it, or the new member sees the same empty
        // container the owner has been staring at (#584). ensureFor re-reads membership, so on a
        // community whose channel is being backfilled right now the bulk-enrol already includes this
        // user and the enrol below is a harmless upsert.
        val channelId = announcementChannel.ensureFor(community)
        announcementChannel.enrol(channelId, userId)

        // Only after the join is actually written — a spent use with no member would silently
        // shrink the link for everyone else.
        inviteLinkRepository.incrementUseCount(link.id)

        log.info("User joined community via invite link: community={}, user={}, link={}", community.id, userId, link.id)

        return CommunitySummary(
            community = community.copy(announcementGroupId = channelId),
            groupCount = communityRepository.countGroupsByCommunityIds(listOf(community.id))[community.id] ?: 0,
            memberCount = memberCountOf(community.id)
        )
    }

    /**
     * Resolves a token to a link that would admit someone right now, or throws saying why not.
     *
     * Shared by [preview] and [accept] so the screen cannot show a joinable community and then
     * refuse the join. A revoked link is reported as `INVITE_LINK_NOT_FOUND` rather than as its own
     * code: to the holder those are the same fact, and distinguishing them would confirm that a
     * given token was once real.
     */
    private fun requireUsableLink(token: String): CommunityInviteLink {
        val link = inviteLinkRepository.findByToken(token)
            ?: throw BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND)

        val now = Instant.now()
        if (!link.isActive) throw BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND)
        if (link.isExpiredAt(now)) throw BusinessException(ErrorCode.INVITE_LINK_EXPIRED)
        if (link.isExhausted()) throw BusinessException(ErrorCode.INVITE_LINK_MAX_USES)
        return link
    }

    /**
     * Refuses to admit someone who has blocked the person that minted the link (#294, #685).
     *
     * `CommunityService.addMember` already refuses this, because enrolment ends in the community's
     * announcement channel — a GROUP conversation the enroller can post to, and reaching a person
     * through a room you put them in is the reach the send path refuses outright. An invite ends in
     * the same seat, by the same two lines below. Without this, everything #685 closed reopens
     * through the new door: mint a link, get it to the person who blocked you by any route they
     * still share with you, and their accept puts them back in a room with you.
     *
     * That the joiner performs the action is not consent to *this*: what a link shows them is a
     * community, and the blocked person may be one admin among several, or the community's name may
     * say nothing about who runs it. The block is the standing instruction; a link is not a request
     * to reconsider it.
     *
     * Directional, like every block check in this codebase: the question is whether the **joiner**
     * blocked the **link's creator**. A creator who blocked the joiner is their own business —
     * they are not the one being reached, and they can revoke the link.
     *
     * Reported as [ErrorCode.INVITE_LINK_NOT_FOUND], the code a revoked link also gets. To the
     * holder these are one fact ("this link does not admit me"); a code of its own would announce
     * which of the two people involved did the blocking. Same reasoning as the shared
     * `COMMUNITY_MEMBER_NOT_IN_ANY_GROUP` on the owner-side add.
     *
     * Judged by `createdBy`, the only participant a link records. A link forwarded by a third party
     * is still judged by who minted it; there is nothing else to judge it by.
     */
    private fun requireInviterNotBlockedBy(link: CommunityInviteLink, userId: UUID) {
        if (blockPolicy.hasBlocked(userId, link.createdBy)) {
            log.info(
                "Community invite refused, the joiner has blocked the link creator: community={}, link={}",
                link.communityId,
                link.id
            )
            throw BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND)
        }
    }

    private fun memberCountOf(communityId: UUID): Int =
        communityRepository.countMembersByCommunityIds(listOf(communityId))[communityId] ?: 0
}
