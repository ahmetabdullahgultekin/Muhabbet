package com.muhabbet.auth.adapter.out.persistence.entity

import com.muhabbet.auth.domain.model.LoginApproval
import com.muhabbet.auth.domain.model.LoginApprovalStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "login_approvals")
class LoginApprovalJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "device_name")
    val deviceName: String? = null,

    @Column(name = "platform")
    val platform: String? = null,

    @Column(name = "ip_address")
    val ipAddress: String? = null,

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: LoginApprovalStatus = LoginApprovalStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant
) {
    fun toDomain(): LoginApproval = LoginApproval(
        id = id, userId = userId, deviceName = deviceName, platform = platform,
        ipAddress = ipAddress, status = status, createdAt = createdAt,
        resolvedAt = resolvedAt, expiresAt = expiresAt
    )

    companion object {
        fun fromDomain(la: LoginApproval): LoginApprovalJpaEntity = LoginApprovalJpaEntity(
            id = la.id, userId = la.userId, deviceName = la.deviceName, platform = la.platform,
            ipAddress = la.ipAddress, status = la.status, createdAt = la.createdAt,
            resolvedAt = la.resolvedAt, expiresAt = la.expiresAt
        )
    }
}
