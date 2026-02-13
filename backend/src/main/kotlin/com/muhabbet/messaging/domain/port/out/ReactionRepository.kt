package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.Reaction
import java.util.UUID

interface ReactionRepository {
    fun save(reaction: Reaction): Reaction
    fun findByMessageId(messageId: UUID): List<Reaction>
    fun findByMessageIdAndUserIdAndEmoji(messageId: UUID, userId: UUID, emoji: String): Reaction?
    fun deleteByMessageIdAndUserIdAndEmoji(messageId: UUID, userId: UUID, emoji: String)
}
