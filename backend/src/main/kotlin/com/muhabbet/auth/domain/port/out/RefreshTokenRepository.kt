package com.muhabbet.auth.domain.port.out

import java.time.Instant
import java.util.UUID

data class RefreshTokenRecord(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val deviceId: UUID,
    val tokenHash: String,
    val expiresAt: Instant,
    val createdAt: Instant = Instant.now(),
    val revokedAt: Instant? = null
)

interface RefreshTokenRepository {
    fun save(record: RefreshTokenRecord): RefreshTokenRecord
    fun findByTokenHash(tokenHash: String): RefreshTokenRecord?
    fun revokeAllForDevice(userId: UUID, deviceId: UUID)
    fun revokeByTokenHash(tokenHash: String)
}
