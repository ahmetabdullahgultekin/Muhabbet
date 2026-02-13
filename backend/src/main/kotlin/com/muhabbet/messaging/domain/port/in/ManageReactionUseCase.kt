package com.muhabbet.messaging.domain.port.`in`

import java.util.UUID

interface ManageReactionUseCase {
    fun addReaction(messageId: UUID, userId: UUID, emoji: String)
    fun removeReaction(messageId: UUID, userId: UUID, emoji: String)
    fun getReactions(messageId: UUID): List<ReactionGroup>
}

data class ReactionGroup(
    val emoji: String,
    val count: Int,
    val userIds: List<UUID>
)
