package com.muhabbet.auth.adapter.out.persistence

import com.muhabbet.auth.adapter.out.persistence.entity.DeviceJpaEntity
import com.muhabbet.auth.adapter.out.persistence.repository.SpringDataDeviceRepository
import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.DevicePlatform
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.shared.security.LiveSession
import com.muhabbet.shared.security.LiveSessionLookup
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class DevicePersistenceAdapter(
    private val springDataDeviceRepository: SpringDataDeviceRepository
) : DeviceRepository, LiveSessionLookup {

    override fun save(device: Device): Device =
        springDataDeviceRepository.save(DeviceJpaEntity.fromDomain(device)).toDomain()

    override fun findActiveByInstallation(userId: UUID, installationId: UUID): Device? =
        springDataDeviceRepository
            .findByUserIdAndInstallationIdAndRevokedAtIsNull(userId, installationId)
            ?.toDomain()

    override fun findLegacyPrimaryCandidate(userId: UUID, platform: DevicePlatform): Device? =
        springDataDeviceRepository.findLegacyPrimaryCandidate(userId, platform.wireName)?.toDomain()

    override fun findByUserId(userId: UUID): List<Device> =
        springDataDeviceRepository.findByUserId(userId).map { it.toDomain() }

    override fun findByUserIdIn(userIds: Collection<UUID>): List<Device> =
        // An empty IN () is not valid SQL and Spring Data would still issue the statement.
        if (userIds.isEmpty()) emptyList()
        else springDataDeviceRepository.findByUserIdIn(userIds).map { it.toDomain() }

    override fun findById(id: UUID): Device? =
        springDataDeviceRepository.findById(id).orElse(null)?.toDomain()

    override fun findActiveByUserId(userId: UUID): List<Device> =
        springDataDeviceRepository.findByUserIdAndRevokedAtIsNull(userId).map { it.toDomain() }

    // Transactional here as well as at the callers: a bump must commit even when it is the only
    // write on its path, and a @Modifying query refuses to run without a transaction.
    @Transactional
    override fun bumpTokenVersion(deviceId: UUID) {
        springDataDeviceRepository.bumpTokenVersion(deviceId)
    }

    @Transactional
    override fun bumpTokenVersionForUser(userId: UUID) {
        springDataDeviceRepository.bumpTokenVersionForUser(userId)
    }

    @Transactional(readOnly = true)
    override fun findLiveSession(userId: UUID, deviceId: UUID): LiveSession? =
        springDataDeviceRepository.findLiveSession(deviceId, userId, UserStatus.ACTIVE)
            ?.let { LiveSession(tokenVersion = it.tokenVersion, isAdmin = it.admin) }
}
