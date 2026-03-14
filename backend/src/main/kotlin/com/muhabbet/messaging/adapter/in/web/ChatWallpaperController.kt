package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.ChatWallpaper
import com.muhabbet.messaging.domain.model.WallpaperType
import com.muhabbet.messaging.domain.port.`in`.ManageChatWallpaperUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class SetWallpaperRequest(
    val type: String = "DEFAULT",
    val value: String? = null,
    val darkModeValue: String? = null
)

data class WallpaperResponse(
    val id: String,
    val conversationId: String?,
    val type: String,
    val value: String?,
    val darkModeValue: String?,
    val createdAt: String
)

@RestController
@RequestMapping("/api/v1/wallpapers")
class ChatWallpaperController(
    private val manageChatWallpaperUseCase: ManageChatWallpaperUseCase
) {

    @GetMapping
    fun getWallpapers(): ResponseEntity<ApiResponse<List<WallpaperResponse>>> {
        val userId = AuthenticatedUser.currentUserId()
        val wallpapers = manageChatWallpaperUseCase.getWallpapers(userId)
        return ApiResponseBuilder.ok(wallpapers.map { it.toResponse() })
    }

    @PutMapping
    fun setGlobalWallpaper(@RequestBody request: SetWallpaperRequest): ResponseEntity<ApiResponse<WallpaperResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val type = WallpaperType.valueOf(request.type.uppercase())
        val wallpaper = manageChatWallpaperUseCase.setGlobalWallpaper(userId, type, request.value, request.darkModeValue)
        return ApiResponseBuilder.ok(wallpaper.toResponse())
    }

    @PutMapping("/conversations/{conversationId}")
    fun setConversationWallpaper(
        @PathVariable conversationId: UUID,
        @RequestBody request: SetWallpaperRequest
    ): ResponseEntity<ApiResponse<WallpaperResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val type = WallpaperType.valueOf(request.type.uppercase())
        val wallpaper = manageChatWallpaperUseCase.setConversationWallpaper(userId, conversationId, type, request.value, request.darkModeValue)
        return ApiResponseBuilder.ok(wallpaper.toResponse())
    }

    @DeleteMapping("/{wallpaperId}")
    fun removeWallpaper(@PathVariable wallpaperId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageChatWallpaperUseCase.removeWallpaper(wallpaperId, userId)
        return ApiResponseBuilder.ok(Unit)
    }
}

private fun ChatWallpaper.toResponse() = WallpaperResponse(
    id = id.toString(),
    conversationId = conversationId?.toString(),
    type = wallpaperType.name,
    value = wallpaperValue,
    darkModeValue = darkModeValue,
    createdAt = createdAt.toString()
)
