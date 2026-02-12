package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

enum class ConversationType {
    DIRECT, GROUP, CHANNEL
}

enum class MemberRole {
    OWNER, ADMIN, MEMBER
}

data class Conversation(
    val id: UUID = UUID.randomUUID(),
    val type: ConversationType,
    val name: String? = null,
    val avatarUrl: String? = null,
    val description: String? = null,
    val createdBy: UUID? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val disappearAfterSeconds: Int? = null
)

data class ConversationMember(
    val conversationId: UUID,
    val userId: UUID,
    val role: MemberRole = MemberRole.MEMBER,
    val joinedAt: Instant = Instant.now(),
    val mutedUntil: Instant? = null,
    val lastReadAt: Instant? = null
)
