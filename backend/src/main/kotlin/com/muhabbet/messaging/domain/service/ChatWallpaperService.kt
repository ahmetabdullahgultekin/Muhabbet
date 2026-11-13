package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ChatWallpaper
import com.muhabbet.messaging.domain.model.WallpaperType
import com.muhabbet.messaging.domain.port.`in`.ManageChatWallpaperUseCase
import com.muhabbet.messaging.domain.port.out.ChatWallpaperRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.transaction.TransactionRunner
import org.slf4j.LoggerFactory
import java.util.UUID

class ChatWallpaperService(
    private val chatWallpaperRepository: ChatWallpaperRepository,
    private val transactions: TransactionRunner
) : ManageChatWallpaperUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun setGlobalWallpaper(userId: UUID, type: WallpaperType, value: String?, darkModeValue: String?): ChatWallpaper = transactions.inTransaction {
        val wallpaper = ChatWallpaper(
            userId = userId,
            conversationId = null,
            wallpaperType = type,
            wallpaperValue = value,
            darkModeValue = darkModeValue
        )
        val saved = chatWallpaperRepository.save(wallpaper)
        log.info("Global wallpaper set for user={}, type={}", userId, type)
        return@inTransaction saved
    }

    override fun setConversationWallpaper(
        userId: UUID,
        conversationId: UUID,
        type: WallpaperType,
        value: String?,
        darkModeValue: String?
    ): ChatWallpaper = transactions.inTransaction {
        val wallpaper = ChatWallpaper(
            userId = userId,
            conversationId = conversationId,
            wallpaperType = type,
            wallpaperValue = value,
            darkModeValue = darkModeValue
        )
        val saved = chatWallpaperRepository.save(wallpaper)
        log.info("Conversation wallpaper set: user={}, conv={}, type={}", userId, conversationId, type)
        return@inTransaction saved
    }

    override fun getWallpapers(userId: UUID): List<ChatWallpaper> = transactions.inReadOnlyTransaction {
        chatWallpaperRepository.findAllByUserId(userId)
    }

    override fun removeWallpaper(wallpaperId: UUID, userId: UUID) {
        transactions.inTransaction<Unit> {
            chatWallpaperRepository.delete(wallpaperId)
            log.info("Wallpaper removed: id={}, user={}", wallpaperId, userId)
        }
    }
}
