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
    var lastSeenAt: Instant? = null
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
        deletedAt = deletedAt
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
            deletedAt = user.deletedAt
        )
    }
}
