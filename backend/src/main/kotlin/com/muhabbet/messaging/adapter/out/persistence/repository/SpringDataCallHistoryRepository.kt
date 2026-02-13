package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.CallHistoryJpaEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface SpringDataCallHistoryRepository : JpaRepository<CallHistoryJpaEntity, UUID> {

    @Query(
        """
        SELECT c FROM CallHistoryJpaEntity c
        WHERE c.callerId = :userId OR c.calleeId = :userId
        ORDER BY c.startedAt DESC
        """
    )
    fun findByUserId(userId: UUID, pageable: Pageable): List<CallHistoryJpaEntity>

    @Query(
        """
        SELECT COUNT(c) FROM CallHistoryJpaEntity c
        WHERE c.callerId = :userId OR c.calleeId = :userId
        """
    )
    fun countByUserId(userId: UUID): Int
}
