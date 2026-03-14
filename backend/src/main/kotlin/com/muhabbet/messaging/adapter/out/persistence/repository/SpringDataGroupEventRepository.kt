package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.GroupEventJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.GroupEventRsvpId
import com.muhabbet.messaging.adapter.out.persistence.entity.GroupEventRsvpJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface SpringDataGroupEventJpaRepository : JpaRepository<GroupEventJpaEntity, UUID> {
    @Query(
        """
        SELECT e FROM GroupEventJpaEntity e
        WHERE e.conversationId = :conversationId
        ORDER BY e.eventTime DESC
        """
    )
    fun findByConversationId(conversationId: UUID): List<GroupEventJpaEntity>
}

interface SpringDataGroupEventRsvpRepository : JpaRepository<GroupEventRsvpJpaEntity, GroupEventRsvpId> {
    fun findByEventId(eventId: UUID): List<GroupEventRsvpJpaEntity>
}
