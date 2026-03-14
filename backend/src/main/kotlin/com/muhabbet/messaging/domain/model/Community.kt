package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

data class Community(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val description: String? = null,
    val avatarUrl: String? = null,
    val createdBy: UUID,
    val announcementGroupId: UUID? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

data class CommunityGroup(
    val communityId: UUID,
    val conversationId: UUID,
    val addedAt: Instant = Instant.now()
)

data class CommunityMember(
    val communityId: UUID,
    val userId: UUID,
    val role: MemberRole = MemberRole.MEMBER,
    val joinedAt: Instant = Instant.now()
)
