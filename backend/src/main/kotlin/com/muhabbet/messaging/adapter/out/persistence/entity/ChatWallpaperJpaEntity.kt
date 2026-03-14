package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.ChatWallpaper
import com.muhabbet.messaging.domain.model.WallpaperType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "chat_wallpapers")
class ChatWallpaperJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "conversation_id")
    val conversationId: UUID? = null,

    @Column(name = "wallpaper_type", nullable = false)
    @Enumerated(EnumType.STRING)
    var wallpaperType: WallpaperType = WallpaperType.DEFAULT,

    @Column(name = "wallpaper_value")
    var wallpaperValue: String? = null,

    @Column(name = "dark_mode_value")
    var darkModeValue: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
) {
    fun toDomain(): ChatWallpaper = ChatWallpaper(
        id = id, userId = userId, conversationId = conversationId,
        wallpaperType = wallpaperType, wallpaperValue = wallpaperValue,
        darkModeValue = darkModeValue, createdAt = createdAt
    )

    companion object {
        fun fromDomain(w: ChatWallpaper): ChatWallpaperJpaEntity = ChatWallpaperJpaEntity(
            id = w.id, userId = w.userId, conversationId = w.conversationId,
            wallpaperType = w.wallpaperType, wallpaperValue = w.wallpaperValue,
            darkModeValue = w.darkModeValue, createdAt = w.createdAt
        )
    }
}
