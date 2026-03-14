package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

enum class WallpaperType {
    DEFAULT, SOLID, CUSTOM
}

data class ChatWallpaper(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val conversationId: UUID? = null,
    val wallpaperType: WallpaperType = WallpaperType.DEFAULT,
    val wallpaperValue: String? = null,
    val darkModeValue: String? = null,
    val createdAt: Instant = Instant.now()
)
