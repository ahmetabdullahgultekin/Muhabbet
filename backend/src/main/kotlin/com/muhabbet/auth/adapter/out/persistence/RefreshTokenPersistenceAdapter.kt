package com.muhabbet.auth.adapter.out.persistence

import com.muhabbet.auth.adapter.out.persistence.entity.RefreshTokenJpaEntity
import com.muhabbet.auth.adapter.out.persistence.repository.SpringDataRefreshTokenRepository
import com.muhabbet.auth.domain.port.out.RefreshTokenRecord
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class RefreshTokenPersistenceAdapter(
    private val springDataRefreshTokenRepository: SpringDataRefreshTokenRepository
) : RefreshTokenRepository {

    override fun save(record: RefreshTokenRecord): RefreshTokenRecord =
        springDataRefreshTokenRepository.save(RefreshTokenJpaEntity.fromDomain(record)).toDomain()

    override fun findByTokenHash(tokenHash: String): RefreshTokenRecord? =
        springDataRefreshTokenRepository.findByTokenHash(tokenHash)?.toDomain()

    override fun findByUserId(userId: UUID): List<RefreshTokenRecord> =
        springDataRefreshTokenRepository.findByUserId(userId).map { it.toDomain() }

    override fun revokeAllForDevice(userId: UUID, deviceId: UUID) {
        springDataRefreshTokenRepository.revokeAllForDevice(userId, deviceId, Instant.now())
    }

    override fun revokeAllForUser(userId: UUID) {
        springDataRefreshTokenRepository.revokeAllForUser(userId, Instant.now())
    }

    override fun markRotated(tokenHash: String, at: Instant): Boolean =
        springDataRefreshTokenRepository.markRotated(tokenHash, at) == 1

    override fun revokeFamily(familyId: UUID, at: Instant) {
        springDataRefreshTokenRepository.revokeFamily(familyId, at)
    }
}
