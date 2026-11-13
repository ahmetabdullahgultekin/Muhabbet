package com.muhabbet.auth.adapter.out.persistence.entity

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.DevicePlatform
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

    /** The [DevicePlatform.wireName]; constrained by V28's `ck_devices_platform`. */
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
    var isPrimary: Boolean = false,

    /** BCP-47 tag for server-generated push text; null = unknown (V22). */
    @Column(name = "locale", length = 16)
    var locale: String? = null,

    // ─── Multi-device (Tier 2, additive nullable columns from V18) ───
    @Column(name = "linked_by_device_id")
    var linkedByDeviceId: UUID? = null,

    @Column(name = "display_name")
    var displayName: String? = null,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,

    /** Client-generated installation id (#882, V28); unique per user among active rows (V29). */
    @Column(name = "installation_id")
    var installationId: UUID? = null,

    /**
     * Access-token epoch (#923, V28). Neither inserted nor updated through the entity: the column
     * default gives a new row 0, and only the `bumpTokenVersion` UPDATE moves it. A `save()` of a
     * domain copy read before a bump therefore cannot write the old epoch back and so resurrect the
     * access tokens the bump revoked.
     */
    @Column(name = "token_version", insertable = false, updatable = false)
    var tokenVersion: Int = 0
) {
    fun toDomain(): Device = Device(
        id = id,
        userId = userId,
        platform = DevicePlatform.parse(platform)
            ?: error("devices.platform holds '$platform', which ck_devices_platform should have refused"),
        deviceName = deviceName,
        pushToken = pushToken,
        lastActiveAt = lastActiveAt,
        createdAt = createdAt,
        isPrimary = isPrimary,
        locale = locale,
        linkedByDeviceId = linkedByDeviceId,
        displayName = displayName,
        revokedAt = revokedAt,
        installationId = installationId,
        tokenVersion = tokenVersion
    )

    companion object {
        fun fromDomain(device: Device): DeviceJpaEntity = DeviceJpaEntity(
            id = device.id,
            userId = device.userId,
            platform = device.platform.wireName,
            deviceName = device.deviceName,
            pushToken = device.pushToken,
            lastActiveAt = device.lastActiveAt,
            createdAt = device.createdAt,
            isPrimary = device.isPrimary,
            locale = device.locale,
            linkedByDeviceId = device.linkedByDeviceId,
            displayName = device.displayName,
            revokedAt = device.revokedAt,
            installationId = device.installationId,
            tokenVersion = device.tokenVersion
        )
    }
}
