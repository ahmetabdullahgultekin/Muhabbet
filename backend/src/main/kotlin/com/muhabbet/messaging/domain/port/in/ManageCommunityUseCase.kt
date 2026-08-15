package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.Community
import com.muhabbet.messaging.domain.model.CommunityGroup
import com.muhabbet.messaging.domain.model.CommunityMember
import com.muhabbet.messaging.domain.model.MemberRole
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
 */
data class CommunityDetails(
    val community: Community,
    val groups: List<CommunityGroupSummary>,
    val memberCount: Int,
    val myRole: MemberRole
)

interface ManageCommunityUseCase {
    fun create(name: String, description: String?, creatorId: UUID): CommunitySummary

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
     * Enrols [userId] in the community. Until an invite flow exists (#387), the target must already
     * be a member of one of the community's groups — see the note in the implementation.
     */
    fun addMember(communityId: UUID, userId: UUID, requesterId: UUID): CommunityMember

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
