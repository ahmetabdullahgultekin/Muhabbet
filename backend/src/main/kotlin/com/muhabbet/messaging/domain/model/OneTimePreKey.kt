package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

data class OneTimePreKey(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val keyId: Int,
    val publicKey: String,
    val used: Boolean = false,
    val createdAt: Instant = Instant.now()
)
