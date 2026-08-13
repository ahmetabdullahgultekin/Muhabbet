package com.muhabbet.auth.adapter.out.persistence.repository

import com.muhabbet.auth.adapter.out.persistence.entity.OtpRequestJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface SpringDataOtpRepository : JpaRepository<OtpRequestJpaEntity, UUID> {

    @Query(
        """
        SELECT o FROM OtpRequestJpaEntity o
        WHERE o.phoneNumber = :phoneNumber
          AND o.verified = false
          AND o.expiresAt > :now
        ORDER BY o.createdAt DESC
        LIMIT 1
        """
    )
    fun findActiveByPhoneNumber(phoneNumber: String, now: Instant): OtpRequestJpaEntity?

    /**
     * Increments in the database rather than from a value the caller read earlier, so two verify
     * requests racing on the same OTP cannot both write the same number and lose one of the attempts.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OtpRequestJpaEntity o SET o.attempts = o.attempts + 1 WHERE o.id = :id")
    fun incrementAttempts(@Param("id") id: UUID): Int
}
