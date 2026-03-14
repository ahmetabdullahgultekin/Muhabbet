package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.ChatWallpaperJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataChatWallpaperRepository : JpaRepository<ChatWallpaperJpaEntity, UUID> {
    fun findByUserIdAndConversationIdIsNull(userId: UUID): ChatWallpaperJpaEntity?
    fun findByUserIdAndConversationId(userId: UUID, conversationId: UUID): ChatWallpaperJpaEntity?
    fun findByUserId(userId: UUID): List<ChatWallpaperJpaEntity>
}
