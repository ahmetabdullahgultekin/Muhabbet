package com.muhabbet.auth.domain.model

import java.time.Instant
import java.util.UUID

data class Device(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val platform: String,
    val deviceName: String? = null,
    val pushToken: String? = null,
    val lastActiveAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val isPrimary: Boolean = false
)
