package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.GroupEventJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.GroupEventRsvpId
import com.muhabbet.messaging.adapter.out.persistence.entity.GroupEventRsvpJpaEntity
import com.muhabbet.messaging.domain.model.RsvpStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    /**
     * One grouped count for the whole page of events, so rendering a list of N events costs one
     * query rather than N.
     */
    @Query(
        """
        SELECT r.eventId, COUNT(r) FROM GroupEventRsvpJpaEntity r
        WHERE r.eventId IN :eventIds AND r.status = :status
        GROUP BY r.eventId
        """
    )
    fun countByStatusGroupedByEventId(
        @Param("eventIds") eventIds: List<UUID>,
        @Param("status") status: RsvpStatus
    ): List<Array<Any>>
}
