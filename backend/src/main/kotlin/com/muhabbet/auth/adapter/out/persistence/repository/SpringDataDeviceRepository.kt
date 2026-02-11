package com.muhabbet.auth.adapter.out.persistence.repository

import com.muhabbet.auth.adapter.out.persistence.entity.DeviceJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataDeviceRepository : JpaRepository<DeviceJpaEntity, UUID> {
    fun findByUserIdAndPlatform(userId: UUID, platform: String): DeviceJpaEntity?
    fun findByUserId(userId: UUID): List<DeviceJpaEntity>
}
