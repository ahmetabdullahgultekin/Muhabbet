package com.muhabbet.auth.adapter.out.persistence

import com.muhabbet.auth.adapter.out.persistence.entity.DeviceLinkSessionJpaEntity
import com.muhabbet.auth.adapter.out.persistence.repository.SpringDataDeviceLinkSessionRepository
import com.muhabbet.auth.domain.model.DeviceLinkSession
import com.muhabbet.auth.domain.port.out.DeviceLinkSessionRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class DeviceLinkSessionPersistenceAdapter(
    private val repo: SpringDataDeviceLinkSessionRepository
) : DeviceLinkSessionRepository {

    override fun save(session: DeviceLinkSession): DeviceLinkSession =
        repo.save(DeviceLinkSessionJpaEntity.fromDomain(session)).toDomain()

    override fun findById(id: UUID): DeviceLinkSession? =
        repo.findById(id).orElse(null)?.toDomain()

    override fun findByLinkToken(token: String): DeviceLinkSession? =
        repo.findByLinkToken(token)?.toDomain()
}
