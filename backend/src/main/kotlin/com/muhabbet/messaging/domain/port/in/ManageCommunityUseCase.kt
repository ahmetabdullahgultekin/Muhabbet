package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.Community
import com.muhabbet.messaging.domain.model.CommunityGroup
import com.muhabbet.messaging.domain.model.CommunityMember
import java.util.UUID

data class CommunityDetails(
    val community: Community,
    val groups: List<CommunityGroup>,
    val members: List<CommunityMember>
)

interface ManageCommunityUseCase {
    fun create(name: String, description: String?, creatorId: UUID): Community
    fun addGroup(communityId: UUID, conversationId: UUID, userId: UUID): CommunityGroup
    fun removeGroup(communityId: UUID, conversationId: UUID, userId: UUID)
    fun addMember(communityId: UUID, userId: UUID, requesterId: UUID): CommunityMember
    fun getDetails(communityId: UUID): CommunityDetails
    fun listForUser(userId: UUID): List<Community>
}
