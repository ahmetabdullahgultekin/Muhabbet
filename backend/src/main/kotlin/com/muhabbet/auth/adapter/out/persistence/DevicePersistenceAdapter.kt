package com.muhabbet.auth.adapter.out.persistence

import com.muhabbet.auth.adapter.out.persistence.entity.DeviceJpaEntity
import com.muhabbet.auth.adapter.out.persistence.repository.SpringDataDeviceRepository
import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.port.out.DeviceRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class DevicePersistenceAdapter(
    private val springDataDeviceRepository: SpringDataDeviceRepository
) : DeviceRepository {

    override fun save(device: Device): Device =
        springDataDeviceRepository.save(DeviceJpaEntity.fromDomain(device)).toDomain()

    override fun findByUserIdAndPlatform(userId: UUID, platform: String): Device? =
        springDataDeviceRepository.findByUserIdAndPlatform(userId, platform)?.toDomain()

    override fun findByUserId(userId: UUID): List<Device> =
        springDataDeviceRepository.findByUserId(userId).map { it.toDomain() }
}
