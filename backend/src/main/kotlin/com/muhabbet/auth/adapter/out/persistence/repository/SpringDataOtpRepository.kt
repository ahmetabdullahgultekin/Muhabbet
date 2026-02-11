package com.muhabbet.auth.adapter.out.persistence.repository

import com.muhabbet.auth.adapter.out.persistence.entity.OtpRequestJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
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
}
