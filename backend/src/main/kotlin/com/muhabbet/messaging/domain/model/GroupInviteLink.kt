package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

data class GroupInviteLink(
    val id: UUID = UUID.randomUUID(),
    val conversationId: UUID,
    val inviteToken: String,
    val createdBy: UUID,
    val requiresApproval: Boolean = false,
    val isActive: Boolean = true,
    val maxUses: Int? = null,
    val useCount: Int = 0,
    val expiresAt: Instant? = null,
    val createdAt: Instant = Instant.now()
)
