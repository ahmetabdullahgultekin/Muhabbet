package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.OneTimePreKey
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "one_time_pre_keys")
class OneTimePreKeyJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "key_id", nullable = false)
    val keyId: Int,

    @Column(name = "public_key", nullable = false)
    val publicKey: String,

    @Column(name = "used", nullable = false)
    val used: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
) {
    fun toDomain(): OneTimePreKey = OneTimePreKey(
        id = id,
        userId = userId,
        keyId = keyId,
        publicKey = publicKey,
        used = used,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(key: OneTimePreKey): OneTimePreKeyJpaEntity = OneTimePreKeyJpaEntity(
            id = key.id,
            userId = key.userId,
            keyId = key.keyId,
            publicKey = key.publicKey,
            used = key.used,
            createdAt = key.createdAt
        )
    }
}
