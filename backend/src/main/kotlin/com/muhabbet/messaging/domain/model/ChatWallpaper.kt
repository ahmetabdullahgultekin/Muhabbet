package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

/**
 * GRADIENT joined the other three in #380, when the mobile picker gained a gradient tab.
 *
 * The column is `VARCHAR(20)` with no check constraint (`V16`), so this needs no migration — but it
 * does need to exist here, or a client sending the type the picker can now produce would be answered
 * with an `IllegalArgumentException` from `valueOf` rather than a stored preference.
 */
enum class WallpaperType {
    DEFAULT, SOLID, GRADIENT, CUSTOM
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
