package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Community
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.out.CommunityRepository
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.shared.validation.ValidationRules
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * The community's announcement channel (#584): finding it, creating it the first time anyone asks,
 * and seating people in it.
 *
 * Extracted from [CommunityService] when a second caller appeared. [CommunityInviteService] enrols
 * whoever accepts an invite, and that has to be the *same* channel, created by the *same* rules,
 * that `create` and `getDetails` produce — two copies of "make a channel if there isn't one" is how
 * a community ends up with two channels and half its members in each. Not a port: this is domain
 * logic over two existing out-ports, not a new thing the outside world provides.
 *
 * Framework-free by the same rule as every other domain type here — no Spring annotations; the
 * transaction is owned by the calling service's `@Transactional` method, and every method below
 * assumes it is already inside one.
 */
class CommunityAnnouncementChannel(
    private val communityRepository: CommunityRepository,
    private val conversationRepository: ConversationRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Returns [community]'s announcement channel, creating it first if this is the first time
     * anything has asked.
     *
     * The channel is a GROUP conversation with `announcementOnly = true` carrying every current
     * community member — owners and admins as conversation ADMIN (the community's OWNER as
     * conversation OWNER too), everyone else as plain MEMBER. `MessageService.sendMessage` already
     * refuses a plain MEMBER on an `announcementOnly` conversation (`MSG_ANNOUNCEMENT_ONLY`), so the
     * "only admins may post" rule is enforced server-side by the ordinary send path rather than by
     * anything special here, and a message that does get through fans out over the same
     * `MessageBroadcaster` every other conversation uses.
     *
     * Idempotent by the `announcementGroupId` check, which is also what makes this the backfill for
     * communities created before #584: called freshly from `create`, and lazily from
     * `getDetails`/`addMember`/`accept` for a community that predates it. No SQL migration reaches
     * into `conversations` to fabricate one.
     *
     * Not safe against two concurrent first-calls on the same community racing into two channels —
     * there is no row lock here. Accepted: this app has no concurrency that makes it likely, and the
     * loser's channel is orphaned rather than destructive.
     */
    fun ensureFor(community: Community): UUID {
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

    /**
     * Seats [userId] in [channelId] as a plain member.
     *
     * `saveMember` upserts, so this is safe to call for someone [ensureFor] has just enrolled in the
     * same request — which is exactly what happens when a community's very first read and its first
     * new member land together.
     */
    fun enrol(channelId: UUID, userId: UUID) {
        conversationRepository.saveMember(
            ConversationMember(conversationId = channelId, userId = userId, role = MemberRole.MEMBER)
        )
    }
}
