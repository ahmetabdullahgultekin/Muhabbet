package com.muhabbet.auth.adapter.out.persistence.repository

import com.muhabbet.auth.adapter.out.persistence.entity.UserJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import com.muhabbet.auth.domain.model.AgeAssuranceLevel
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface SpringDataUserRepository : JpaRepository<UserJpaEntity, UUID> {
    fun findByPhoneNumber(phoneNumber: String): UserJpaEntity?
    fun existsByPhoneNumber(phoneNumber: String): Boolean

    @Modifying
    @Query("UPDATE UserJpaEntity u SET u.lastSeenAt = :lastSeenAt WHERE u.id = :userId")
    fun updateLastSeenAt(userId: UUID, lastSeenAt: Instant)

    @Modifying
    @Query(
        "UPDATE UserJpaEntity u SET u.birthDate = :birthDate, u.ageAssuranceLevel = :level, " +
            "u.ageAssuredAt = :assuredAt WHERE u.id = :userId AND u.birthDate IS NULL"
    )
    fun recordBirthDate(
        userId: UUID,
        birthDate: LocalDate,
        level: AgeAssuranceLevel?,
        assuredAt: Instant?
    ): Int
}
