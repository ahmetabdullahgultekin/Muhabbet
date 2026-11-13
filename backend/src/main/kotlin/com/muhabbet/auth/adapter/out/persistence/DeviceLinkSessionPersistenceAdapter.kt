package com.muhabbet.auth.adapter.out.persistence

import com.muhabbet.auth.adapter.out.persistence.entity.DeviceLinkSessionJpaEntity
import com.muhabbet.auth.adapter.out.persistence.repository.SpringDataDeviceLinkSessionRepository
import com.muhabbet.auth.domain.model.DeviceLinkSession
import com.muhabbet.auth.domain.port.out.DeviceLinkSessionRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class DeviceLinkSessionPersistenceAdapter(
    private val repo: SpringDataDeviceLinkSessionRepository
) : DeviceLinkSessionRepository {

    override fun save(session: DeviceLinkSession): DeviceLinkSession =
        repo.save(DeviceLinkSessionJpaEntity.fromDomain(session)).toDomain()

    override fun findById(id: UUID): DeviceLinkSession? =
        repo.findById(id).orElse(null)?.toDomain()

    override fun findByTokenHash(tokenHash: String): DeviceLinkSession? =
        repo.findByTokenHash(tokenHash)?.toDomain()

    override fun claimCompletion(completed: DeviceLinkSession, now: Instant): Boolean {
        val linkedDeviceId = completed.linkedDeviceId ?: return false
        val completedAt = completed.completedAt ?: return false
        return repo.claimCompletion(
            id = completed.id,
            companionPlatform = completed.companionPlatform,
            companionDeviceName = completed.companionDeviceName,
            publicBundle = completed.publicBundle,
            linkedDeviceId = linkedDeviceId,
            completedAt = completedAt,
            now = now
        ) == 1
    }

    override fun findByUserId(userId: UUID): List<DeviceLinkSession> =
        repo.findByUserId(userId).map { it.toDomain() }
}
