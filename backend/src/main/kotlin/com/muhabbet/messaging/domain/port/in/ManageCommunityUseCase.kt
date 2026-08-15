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
 * @param myRole the requester's role in this community, or `null` when the requester is not a
 * member. A non-member can still read a community, so absence of a role is a valid answer and
 * not an error.
 */
data class CommunityDetails(
    val community: Community,
    val groups: List<CommunityGroupSummary>,
    val memberCount: Int,
    val myRole: MemberRole?
)

interface ManageCommunityUseCase {
    fun create(name: String, description: String?, creatorId: UUID): CommunitySummary
    fun addGroup(communityId: UUID, conversationId: UUID, userId: UUID): CommunityGroup
    fun removeGroup(communityId: UUID, conversationId: UUID, userId: UUID)
    fun addMember(communityId: UUID, userId: UUID, requesterId: UUID): CommunityMember
    fun getDetails(communityId: UUID, userId: UUID): CommunityDetails
    fun listForUser(userId: UUID): List<CommunitySummary>
}
