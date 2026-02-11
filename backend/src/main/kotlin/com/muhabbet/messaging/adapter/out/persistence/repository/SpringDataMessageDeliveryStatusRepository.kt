package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageDeliveryStatusId
import com.muhabbet.messaging.adapter.out.persistence.entity.MessageDeliveryStatusJpaEntity
import com.muhabbet.messaging.domain.model.DeliveryStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface SpringDataMessageDeliveryStatusRepository : JpaRepository<MessageDeliveryStatusJpaEntity, MessageDeliveryStatusId> {

    fun findByMessageIdAndUserId(messageId: UUID, userId: UUID): MessageDeliveryStatusJpaEntity?

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
}
