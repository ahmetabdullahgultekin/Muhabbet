package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageDeliveryStatusId
import com.muhabbet.messaging.adapter.out.persistence.entity.MessageDeliveryStatusJpaEntity
import com.muhabbet.messaging.domain.model.DeliveryStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface SpringDataMessageDeliveryStatusRepository : JpaRepository<MessageDeliveryStatusJpaEntity, MessageDeliveryStatusId> {

    fun findByMessageIdAndUserId(messageId: UUID, userId: UUID): MessageDeliveryStatusJpaEntity?

    fun findByMessageIdIn(messageIds: List<UUID>): List<MessageDeliveryStatusJpaEntity>

    @Query(
        """
        SELECT COUNT(m) FROM MessageJpaEntity m
        LEFT JOIN MessageDeliveryStatusJpaEntity ds ON ds.messageId = m.id AND ds.userId = :userId
        WHERE m.conversationId = :conversationId
          AND m.isDeleted = false
          AND m.senderId != :userId
          AND (ds.status IS NULL OR ds.status != :readStatus)
        """
    )
    fun countUnread(conversationId: UUID, userId: UUID, readStatus: DeliveryStatus): Int

    // Batch unread counts for inbox (replaces N individual countUnread calls)
    @Query(
        """
        SELECT m.conversationId, COUNT(m) FROM MessageJpaEntity m
        LEFT JOIN MessageDeliveryStatusJpaEntity ds ON ds.messageId = m.id AND ds.userId = :userId
        WHERE m.conversationId IN :conversationIds
          AND m.isDeleted = false
          AND m.senderId != :userId
          AND (ds.status IS NULL OR ds.status != :readStatus)
        GROUP BY m.conversationId
        """
    )
    fun countUnreadByConversations(conversationIds: List<UUID>, userId: UUID, readStatus: DeliveryStatus): List<Array<Any>>

    @Modifying
    @Query(
        """
        UPDATE MessageDeliveryStatusJpaEntity ds
        SET ds.status = :newStatus, ds.updatedAt = :now
        WHERE ds.userId = :userId
          AND ds.status != :newStatus
          AND ds.messageId IN (
            SELECT m.id FROM MessageJpaEntity m
            WHERE m.conversationId = :conversationId
              AND m.senderId != :userId
              AND m.isDeleted = false
          )
        """
    )
    fun markAllAsRead(conversationId: UUID, userId: UUID, newStatus: DeliveryStatus, now: Instant): Int
}
