package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

data class EncryptionKeyBundle(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val identityKey: String,
    val signedPreKey: String,
    val signedPreKeyId: Int,
    val registrationId: Int,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
