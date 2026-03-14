package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.ChatWallpaperJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataChatWallpaperRepository
import com.muhabbet.messaging.domain.model.ChatWallpaper
import com.muhabbet.messaging.domain.port.out.ChatWallpaperRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ChatWallpaperPersistenceAdapter(
    private val repo: SpringDataChatWallpaperRepository
) : ChatWallpaperRepository {

    override fun save(wallpaper: ChatWallpaper): ChatWallpaper {
        // Upsert: if existing wallpaper for same user+conversation, update it
        val existing = if (wallpaper.conversationId != null) {
            repo.findByUserIdAndConversationId(wallpaper.userId, wallpaper.conversationId)
        } else {
            repo.findByUserIdAndConversationIdIsNull(wallpaper.userId)
        }

        if (existing != null) {
            existing.wallpaperType = wallpaper.wallpaperType
            existing.wallpaperValue = wallpaper.wallpaperValue
            existing.darkModeValue = wallpaper.darkModeValue
            return repo.save(existing).toDomain()
        }

        return repo.save(ChatWallpaperJpaEntity.fromDomain(wallpaper)).toDomain()
    }

    override fun findGlobalForUser(userId: UUID): ChatWallpaper? =
        repo.findByUserIdAndConversationIdIsNull(userId)?.toDomain()

    override fun findForConversation(userId: UUID, conversationId: UUID): ChatWallpaper? =
        repo.findByUserIdAndConversationId(userId, conversationId)?.toDomain()

    override fun findAllByUserId(userId: UUID): List<ChatWallpaper> =
        repo.findByUserId(userId).map { it.toDomain() }

    override fun delete(id: UUID) =
        repo.deleteById(id)
}
