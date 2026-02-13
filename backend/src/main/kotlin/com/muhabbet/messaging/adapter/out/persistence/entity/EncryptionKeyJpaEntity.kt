package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.EncryptionKeyBundle
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "encryption_keys")
class EncryptionKeyJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "identity_key", nullable = false)
    val identityKey: String,

    @Column(name = "signed_pre_key", nullable = false)
    val signedPreKey: String,

    @Column(name = "signed_pre_key_id", nullable = false)
    val signedPreKeyId: Int,

    @Column(name = "registration_id", nullable = false)
    val registrationId: Int,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
) {
    fun toDomain(): EncryptionKeyBundle = EncryptionKeyBundle(
        id = id,
        userId = userId,
        identityKey = identityKey,
        signedPreKey = signedPreKey,
        signedPreKeyId = signedPreKeyId,
        registrationId = registrationId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(bundle: EncryptionKeyBundle): EncryptionKeyJpaEntity = EncryptionKeyJpaEntity(
            id = bundle.id,
            userId = bundle.userId,
            identityKey = bundle.identityKey,
            signedPreKey = bundle.signedPreKey,
            signedPreKeyId = bundle.signedPreKeyId,
            registrationId = bundle.registrationId,
            createdAt = bundle.createdAt,
            updatedAt = bundle.updatedAt
        )
    }
}
