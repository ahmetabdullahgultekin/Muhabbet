package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.Community
import com.muhabbet.messaging.domain.model.CommunityGroup
import com.muhabbet.messaging.domain.model.CommunityMember
import java.util.UUID

interface CommunityRepository {
    fun save(community: Community): Community
    fun findById(id: UUID): Community?
    fun update(community: Community): Community

    fun saveMember(member: CommunityMember): CommunityMember
    fun findMember(communityId: UUID, userId: UUID): CommunityMember?
    fun findMembersByCommunityId(communityId: UUID): List<CommunityMember>
    fun findCommunitiesByUserId(userId: UUID): List<Community>

    fun addGroup(group: CommunityGroup): CommunityGroup
    fun removeGroup(communityId: UUID, conversationId: UUID)
    fun findGroupsByCommunityId(communityId: UUID): List<CommunityGroup>
}
