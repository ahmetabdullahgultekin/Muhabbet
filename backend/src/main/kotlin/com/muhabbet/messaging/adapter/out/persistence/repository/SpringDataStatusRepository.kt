package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.StatusJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface SpringDataStatusRepository : JpaRepository<StatusJpaEntity, UUID> {

    @Query(
        """
        SELECT s FROM StatusJpaEntity s
        WHERE s.userId = :userId AND s.expiresAt > :now
        ORDER BY s.createdAt DESC
        """
    )
    fun findActiveByUserId(userId: UUID, now: Instant = Instant.now()): List<StatusJpaEntity>

    @Query(
        """
        SELECT s FROM StatusJpaEntity s
        WHERE s.userId IN :userIds AND s.expiresAt > :now
        ORDER BY s.createdAt DESC
        """
    )
    fun findActiveByUserIds(userIds: List<UUID>, now: Instant = Instant.now()): List<StatusJpaEntity>

    @Modifying
    @Query("DELETE FROM StatusJpaEntity s WHERE s.expiresAt <= :now")
    fun deleteExpired(now: Instant = Instant.now())
}
