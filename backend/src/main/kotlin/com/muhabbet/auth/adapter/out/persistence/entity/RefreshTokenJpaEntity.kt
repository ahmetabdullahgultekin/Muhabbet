package com.muhabbet.auth.adapter.out.persistence.entity

import com.muhabbet.auth.domain.port.out.RefreshTokenRecord
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenJpaEntity(
    @Id
    val id: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "device_id", nullable = false)
    val deviceId: UUID,

    @Column(name = "token_hash", nullable = false, unique = true)
    val tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null
) {
    fun toDomain(): RefreshTokenRecord = RefreshTokenRecord(
        id = id,
        userId = userId,
        deviceId = deviceId,
        tokenHash = tokenHash,
        expiresAt = expiresAt,
        createdAt = createdAt,
        revokedAt = revokedAt
    )

    companion object {
        fun fromDomain(record: RefreshTokenRecord): RefreshTokenJpaEntity = RefreshTokenJpaEntity(
            id = record.id,
            userId = record.userId,
            deviceId = record.deviceId,
            tokenHash = record.tokenHash,
            expiresAt = record.expiresAt,
            createdAt = record.createdAt,
            revokedAt = record.revokedAt
        )
    }
}
