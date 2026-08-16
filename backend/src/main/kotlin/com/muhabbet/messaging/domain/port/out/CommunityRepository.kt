package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.Community
import com.muhabbet.messaging.domain.model.CommunityGroup
import com.muhabbet.messaging.domain.model.CommunityMember
import java.util.UUID

interface CommunityRepository {
    fun save(community: Community): Community
    fun findById(id: UUID): Community?
    fun update(community: Community): Community

    /** Insert or update. Saving an existing (communityId, userId) pair changes that member's role. */
    fun saveMember(member: CommunityMember): CommunityMember
    fun findMember(communityId: UUID, userId: UUID): CommunityMember?
    fun findMembersByCommunityId(communityId: UUID): List<CommunityMember>
    fun removeMember(communityId: UUID, userId: UUID)
    fun findCommunitiesByUserId(userId: UUID): List<Community>

    fun addGroup(group: CommunityGroup): CommunityGroup
    fun removeGroup(communityId: UUID, conversationId: UUID)
    fun findGroupsByCommunityId(communityId: UUID): List<CommunityGroup>

    /**
     * Member counts for many communities in one query. Communities with no members are absent
     * from the map rather than mapped to zero.
     */
    fun countMembersByCommunityIds(communityIds: List<UUID>): Map<UUID, Int>

    /**
     * Group counts for many communities in one query. Communities with no groups are absent
     * from the map rather than mapped to zero.
     */
    fun countGroupsByCommunityIds(communityIds: List<UUID>): Map<UUID, Int>
}
