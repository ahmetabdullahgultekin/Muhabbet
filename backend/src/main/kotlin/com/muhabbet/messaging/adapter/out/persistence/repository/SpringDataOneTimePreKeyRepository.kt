package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.OneTimePreKeyJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface SpringDataOneTimePreKeyRepository : JpaRepository<OneTimePreKeyJpaEntity, UUID> {

    @Query("SELECT k FROM OneTimePreKeyJpaEntity k WHERE k.userId = :userId AND k.used = false ORDER BY k.createdAt ASC")
    fun findUnusedByUserId(userId: UUID): List<OneTimePreKeyJpaEntity>

    fun countByUserIdAndUsedFalse(userId: UUID): Int
}
