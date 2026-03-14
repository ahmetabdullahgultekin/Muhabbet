package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.ChatWallpaper
import com.muhabbet.messaging.domain.model.WallpaperType
import java.util.UUID

interface ManageChatWallpaperUseCase {
    fun setGlobalWallpaper(userId: UUID, type: WallpaperType, value: String?, darkModeValue: String?): ChatWallpaper
    fun setConversationWallpaper(userId: UUID, conversationId: UUID, type: WallpaperType, value: String?, darkModeValue: String?): ChatWallpaper
    fun getWallpapers(userId: UUID): List<ChatWallpaper>
    fun removeWallpaper(wallpaperId: UUID, userId: UUID)
}
