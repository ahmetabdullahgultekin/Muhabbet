package com.muhabbet.auth.adapter.out.persistence.entity

import com.muhabbet.auth.domain.model.OtpRequest
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "otp_requests")
class OtpRequestJpaEntity(
    @Id
    val id: UUID,

    @Column(name = "phone_number", nullable = false)
    val phoneNumber: String,

    @Column(name = "otp_hash", nullable = false)
    val otpHash: String,

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "verified", nullable = false)
    var verified: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
) {
    fun toDomain(): OtpRequest = OtpRequest(
        id = id,
        phoneNumber = phoneNumber,
        otpHash = otpHash,
        attempts = attempts,
        expiresAt = expiresAt,
        verified = verified,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(otp: OtpRequest): OtpRequestJpaEntity = OtpRequestJpaEntity(
            id = otp.id,
            phoneNumber = otp.phoneNumber,
            otpHash = otp.otpHash,
            attempts = otp.attempts,
            expiresAt = otp.expiresAt,
            verified = otp.verified,
            createdAt = otp.createdAt
        )
    }
}
