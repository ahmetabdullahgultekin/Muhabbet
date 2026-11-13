package com.muhabbet.messaging.domain.port.`in`

import java.util.UUID

interface ManageReactionUseCase {
    /**
     * Both writes answer with every reaction on the message **after** the change, so the caller can
     * hand the client the server's truth instead of the client's guess (#783).
     */
    fun addReaction(messageId: UUID, userId: UUID, emoji: String): List<ReactionGroup>
    fun removeReaction(messageId: UUID, userId: UUID, emoji: String): List<ReactionGroup>

    /**
     * [userId] is the caller, not a filter: the result names everyone who reacted, so it is only
     * returned to a member of the message's conversation. Throws MSG_NOT_MEMBER otherwise (#557).
     */
    fun getReactions(messageId: UUID, userId: UUID): List<ReactionGroup>
}

data class ReactionGroup(
    val emoji: String,
    val count: Int,
    val userIds: List<UUID>
)
