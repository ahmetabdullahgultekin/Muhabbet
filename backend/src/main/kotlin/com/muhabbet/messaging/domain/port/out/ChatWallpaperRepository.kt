package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.ChatWallpaper
import java.util.UUID

interface ChatWallpaperRepository {
    fun save(wallpaper: ChatWallpaper): ChatWallpaper
    fun findGlobalForUser(userId: UUID): ChatWallpaper?
    fun findForConversation(userId: UUID, conversationId: UUID): ChatWallpaper?
    fun findAllByUserId(userId: UUID): List<ChatWallpaper>
    fun delete(id: UUID)
}
