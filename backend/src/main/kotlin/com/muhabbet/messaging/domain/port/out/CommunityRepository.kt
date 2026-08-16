package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.Community
import com.muhabbet.messaging.domain.model.CommunityGroup
import com.muhabbet.messaging.domain.model.CommunityMember
import java.util.UUID

interface CommunityRepository {
    fun save(community: Community): Community
    fun findById(id: UUID): Community?
    fun update(community: Community): Community

    /**
     * Deletes the community itself. `community_members` and `community_groups` both declare
     * `community_id ... REFERENCES communities(id) ON DELETE CASCADE` (`V16`), so this single
     * statement is enough to remove every membership and every group link — the database does the
     * cascading, not this adapter. `community_groups.conversation_id` carries no such clause, so
     * the linked conversations (and their messages and members) are never touched.
     */
    fun delete(id: UUID)

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
