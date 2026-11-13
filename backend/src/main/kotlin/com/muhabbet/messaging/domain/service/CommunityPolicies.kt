package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Community
import com.muhabbet.messaging.domain.model.CommunityMember
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.out.CommunityRepository
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.validation.ValidationRules
import org.slf4j.LoggerFactory
import java.util.UUID

/*
 * The rules the three community services share (#894). They were private members of one
 * 482-line `CommunityService`; splitting it by use case gave each rule a home of its own, composed
 * rather than inherited.
 */

/** Who may do what to a community. Every refusal is `COMMUNITY_PERMISSION_DENIED`. */
class CommunityAccess(private val communityRepository: CommunityRepository) {

    fun requireCommunity(communityId: UUID): Community =
        communityRepository.findById(communityId)
            ?: throw BusinessException(ErrorCode.COMMUNITY_NOT_FOUND)

    fun requireMember(communityId: UUID, userId: UUID): CommunityMember =
        communityRepository.findMember(communityId, userId)
            ?: throw BusinessException(ErrorCode.COMMUNITY_PERMISSION_DENIED)

    fun requireAdminOrOwner(communityId: UUID, userId: UUID): CommunityMember {
        val member = requireMember(communityId, userId)
        if (!member.administers()) {
            throw BusinessException(ErrorCode.COMMUNITY_PERMISSION_DENIED)
        }
        return member
    }

    fun requireOwner(communityId: UUID, userId: UUID): CommunityMember {
        val member = requireMember(communityId, userId)
        if (member.role != MemberRole.OWNER) {
            throw BusinessException(ErrorCode.COMMUNITY_PERMISSION_DENIED)
        }
        return member
    }
}

/** A community's announcement channel (#584). */
class CommunityAnnouncementChannel(
    private val communityRepository: CommunityRepository,
    private val conversationRepository: ConversationRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Returns [community]'s announcement channel, creating it first if this is the first time
     * anything has asked (#584). The channel is a GROUP conversation, `announcementOnly = true`, that
     * carries every current community member — owners and admins seated as conversation ADMIN (one
     * of them, the community's own OWNER, as conversation OWNER too), everyone else as a plain
     * MEMBER. `SendMessageService.sendMessage` already refuses a plain MEMBER on an `announcementOnly`
     * conversation (`MSG_ANNOUNCEMENT_ONLY`), and a message that does get through fans out over the
     * same `MessageBroadcaster` path every other conversation uses — nothing new was needed on
     * either side of the send path for this to work.
     *
     * Idempotent by the `announcementGroupId` check, which is also what makes this the backfill for
     * communities created before this existed: called from `create` (freshly, for the member just
     * added) and from `getDetails`/`addMember` (lazily, for a community that predates it), never a
     * SQL migration reaching into `conversations`. Shared by [CommunityService] and
     * [CommunityMembershipService], which is why it is a policy of its own (#894).
     *
     * Not safe against two concurrent first-calls on the same community racing each other into two
     * channels — there is no row lock here. Accepted for now: this app has no concurrent-access
     * volume that makes it likely, and the loser's channel is merely orphaned, not destructive.
     */
    fun ensure(community: Community): UUID {
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
}
