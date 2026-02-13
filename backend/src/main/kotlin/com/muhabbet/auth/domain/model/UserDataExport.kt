package com.muhabbet.auth.domain.model

import java.time.Instant

data class UserDataExport(
    val userId: String,
    val phoneNumber: String,
    val displayName: String?,
    val avatarUrl: String?,
    val about: String?,
    val messageCount: Long,
    val conversationCount: Long,
    val mediaCount: Long,
    val joinedAt: Instant,
    val exportedAt: Instant = Instant.now()
)
