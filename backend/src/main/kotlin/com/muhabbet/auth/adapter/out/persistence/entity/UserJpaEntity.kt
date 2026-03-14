package com.muhabbet.auth.adapter.out.persistence.entity

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class UserJpaEntity(
    @Id
    val id: UUID,

    @Column(name = "phone_number", nullable = false, unique = true)
    val phoneNumber: String,

    @Column(name = "display_name")
    var displayName: String? = null,

    @Column(name = "avatar_url")
    var avatarUrl: String? = null,

    @Column(name = "about")
    var about: String? = null,

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: UserStatus = UserStatus.ACTIVE,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,

    @Column(name = "last_seen_at")
    var lastSeenAt: Instant? = null,

    // Two-Step Verification
    @Column(name = "two_step_pin_hash")
    var twoStepPinHash: String? = null,

    @Column(name = "two_step_email")
    var twoStepEmail: String? = null,

    @Column(name = "two_step_enabled_at")
    var twoStepEnabledAt: Instant? = null,

    // Privacy Settings
    @Column(name = "read_receipts_enabled", nullable = false)
    var readReceiptsEnabled: Boolean = true,

    @Column(name = "online_status_visibility", nullable = false)
    var onlineStatusVisibility: String = "everyone",

    @Column(name = "about_visibility", nullable = false)
    var aboutVisibility: String = "everyone"
) {
    fun toDomain(): User = User(
        id = id,
        phoneNumber = phoneNumber,
        displayName = displayName,
        avatarUrl = avatarUrl,
        about = about,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        lastSeenAt = lastSeenAt,
        twoStepPinHash = twoStepPinHash,
        twoStepEmail = twoStepEmail,
        twoStepEnabledAt = twoStepEnabledAt,
        readReceiptsEnabled = readReceiptsEnabled,
        onlineStatusVisibility = onlineStatusVisibility,
        aboutVisibility = aboutVisibility
    )

    companion object {
        fun fromDomain(user: User): UserJpaEntity = UserJpaEntity(
            id = user.id,
            phoneNumber = user.phoneNumber,
            displayName = user.displayName,
            avatarUrl = user.avatarUrl,
            about = user.about,
            status = user.status,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt,
            deletedAt = user.deletedAt,
            lastSeenAt = user.lastSeenAt,
            twoStepPinHash = user.twoStepPinHash,
            twoStepEmail = user.twoStepEmail,
            twoStepEnabledAt = user.twoStepEnabledAt,
            readReceiptsEnabled = user.readReceiptsEnabled,
            onlineStatusVisibility = user.onlineStatusVisibility,
            aboutVisibility = user.aboutVisibility
        )
    }
}
