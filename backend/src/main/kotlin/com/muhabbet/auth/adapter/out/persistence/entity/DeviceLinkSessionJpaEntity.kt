package com.muhabbet.auth.adapter.out.persistence.entity

import com.muhabbet.auth.domain.model.DeviceLinkSession
import com.muhabbet.auth.domain.model.DeviceLinkStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "device_link_sessions")
class DeviceLinkSessionJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "primary_device_id", nullable = false)
    val primaryDeviceId: UUID,

    @Column(name = "link_token", nullable = false, unique = true)
    val linkToken: String,

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: DeviceLinkStatus = DeviceLinkStatus.PENDING,

    @Column(name = "companion_platform")
    var companionPlatform: String? = null,

    @Column(name = "companion_device_name")
    var companionDeviceName: String? = null,

    @Column(name = "public_bundle")
    var publicBundle: String? = null,

    @Column(name = "linked_device_id")
    var linkedDeviceId: UUID? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "completed_at")
    var completedAt: Instant? = null
) {
    fun toDomain(): DeviceLinkSession = DeviceLinkSession(
        id = id,
        userId = userId,
        primaryDeviceId = primaryDeviceId,
        linkToken = linkToken,
        status = status,
        companionPlatform = companionPlatform,
        companionDeviceName = companionDeviceName,
        publicBundle = publicBundle,
        linkedDeviceId = linkedDeviceId,
        createdAt = createdAt,
        expiresAt = expiresAt,
        completedAt = completedAt
    )

    companion object {
        fun fromDomain(s: DeviceLinkSession): DeviceLinkSessionJpaEntity = DeviceLinkSessionJpaEntity(
            id = s.id,
            userId = s.userId,
            primaryDeviceId = s.primaryDeviceId,
            linkToken = s.linkToken,
            status = s.status,
            companionPlatform = s.companionPlatform,
            companionDeviceName = s.companionDeviceName,
            publicBundle = s.publicBundle,
            linkedDeviceId = s.linkedDeviceId,
            createdAt = s.createdAt,
            expiresAt = s.expiresAt,
            completedAt = s.completedAt
        )
    }
}
