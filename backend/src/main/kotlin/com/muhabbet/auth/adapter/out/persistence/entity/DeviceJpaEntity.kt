package com.muhabbet.auth.adapter.out.persistence.entity

import com.muhabbet.auth.domain.model.Device
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "devices")
class DeviceJpaEntity(
    @Id
    val id: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "platform", nullable = false)
    val platform: String,

    @Column(name = "device_name")
    var deviceName: String? = null,

    @Column(name = "push_token")
    var pushToken: String? = null,

    @Column(name = "last_active_at")
    var lastActiveAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "is_primary", nullable = false)
    var isPrimary: Boolean = false
) {
    fun toDomain(): Device = Device(
        id = id,
        userId = userId,
        platform = platform,
        deviceName = deviceName,
        pushToken = pushToken,
        lastActiveAt = lastActiveAt,
        createdAt = createdAt,
        isPrimary = isPrimary
    )

    companion object {
        fun fromDomain(device: Device): DeviceJpaEntity = DeviceJpaEntity(
            id = device.id,
            userId = device.userId,
            platform = device.platform,
            deviceName = device.deviceName,
            pushToken = device.pushToken,
            lastActiveAt = device.lastActiveAt,
            createdAt = device.createdAt,
            isPrimary = device.isPrimary
        )
    }
}
