package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageReactionJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface SpringDataReactionRepository : JpaRepository<MessageReactionJpaEntity, UUID> {
    fun findByMessageId(messageId: UUID): List<MessageReactionJpaEntity>
    fun findByMessageIdIn(messageIds: Collection<UUID>): List<MessageReactionJpaEntity>
    fun deleteByMessageIdAndUserIdAndEmoji(messageId: UUID, userId: UUID, emoji: String)

    /** Returns 1 when the row was written, 0 when the same reaction already existed. */
    @Modifying
    @Query(
        value = """
            INSERT INTO message_reactions (id, message_id, user_id, emoji, created_at)
            VALUES (:id, :messageId, :userId, :emoji, :createdAt)
            ON CONFLICT (message_id, user_id, emoji) DO NOTHING
        """,
        nativeQuery = true
    )
    fun insertIfAbsent(id: UUID, messageId: UUID, userId: UUID, emoji: String, createdAt: Instant): Int
}
