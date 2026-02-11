package com.muhabbet.auth.domain.port.out

import com.muhabbet.auth.domain.model.Device
import java.util.UUID

interface DeviceRepository {
    fun save(device: Device): Device
    fun findByUserIdAndPlatform(userId: UUID, platform: String): Device?
    fun findByUserId(userId: UUID): List<Device>
}
