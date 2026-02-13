package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageReactionJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataReactionRepository : JpaRepository<MessageReactionJpaEntity, UUID> {
    fun findByMessageId(messageId: UUID): List<MessageReactionJpaEntity>
    fun findByMessageIdAndUserIdAndEmoji(messageId: UUID, userId: UUID, emoji: String): MessageReactionJpaEntity?
    fun deleteByMessageIdAndUserIdAndEmoji(messageId: UUID, userId: UUID, emoji: String)
}
