package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ChatWallpaper
import com.muhabbet.messaging.domain.model.WallpaperType
import com.muhabbet.messaging.domain.port.`in`.ManageChatWallpaperUseCase
import com.muhabbet.messaging.domain.port.out.ChatWallpaperRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

open class ChatWallpaperService(
    private val chatWallpaperRepository: ChatWallpaperRepository
) : ManageChatWallpaperUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun setGlobalWallpaper(userId: UUID, type: WallpaperType, value: String?, darkModeValue: String?): ChatWallpaper {
        val wallpaper = ChatWallpaper(
            userId = userId,
            conversationId = null,
            wallpaperType = type,
            wallpaperValue = value,
            darkModeValue = darkModeValue
        )
        val saved = chatWallpaperRepository.save(wallpaper)
        log.info("Global wallpaper set for user={}, type={}", userId, type)
        return saved
    }

    @Transactional
    override fun setConversationWallpaper(
        userId: UUID,
        conversationId: UUID,
        type: WallpaperType,
        value: String?,
        darkModeValue: String?
    ): ChatWallpaper {
        val wallpaper = ChatWallpaper(
            userId = userId,
            conversationId = conversationId,
            wallpaperType = type,
            wallpaperValue = value,
            darkModeValue = darkModeValue
        )
        val saved = chatWallpaperRepository.save(wallpaper)
        log.info("Conversation wallpaper set: user={}, conv={}, type={}", userId, conversationId, type)
        return saved
    }

    @Transactional(readOnly = true)
    override fun getWallpapers(userId: UUID): List<ChatWallpaper> =
        chatWallpaperRepository.findAllByUserId(userId)

    @Transactional
    override fun removeWallpaper(wallpaperId: UUID, userId: UUID) {
        chatWallpaperRepository.delete(wallpaperId)
        log.info("Wallpaper removed: id={}, user={}", wallpaperId, userId)
    }
}
