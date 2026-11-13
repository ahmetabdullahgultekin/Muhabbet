package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.ChatWallpaper
import com.muhabbet.messaging.domain.model.WallpaperType
import com.muhabbet.messaging.domain.port.`in`.ManageChatWallpaperUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.SetWallpaperRequest
import com.muhabbet.shared.dto.WallpaperResponse
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.model.WallpaperType as SharedWallpaperType
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

/**
 * Deliberately unused by the mobile client (#380), and now safe to use.
 *
 * The wallpaper picker persists and reads its selection through device-local storage
 * (`WallpaperRepository`/`TokenStorage` on mobile) rather than this endpoint. The reasoning, and what
 * it costs the user, is written down on `WallpaperRepository`; the short version is that the two
 * things a round trip would buy — a second device and a new phone — are respectively gated on the
 * paused multi-device work and only half-solvable while a CUSTOM wallpaper's bytes are device-local.
 *
 * What changed here is that "unused" no longer means "untested and wrong". This file used to declare
 * its own `SetWallpaperRequest`/`WallpaperResponse`, named `type`/`value`, shadowing the shared DTOs
 * of the same names that any mobile client would serialise (`wallpaperType`/`wallpaperValue`). The
 * first real call would have deserialised into `type = "DEFAULT"` and wiped the wallpaper it was sent
 * to set — the #377 shape, where a private copy of a shared DTO quietly disagrees with it. It now
 * uses the shared classes, so the contract this vertical advertises is the one it implements.
 */
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
        val type = request.toWallpaperType()
        val wallpaper = manageChatWallpaperUseCase.setGlobalWallpaper(userId, type, request.wallpaperValue, request.darkModeValue)
        return ApiResponseBuilder.ok(wallpaper.toResponse())
    }

    @PutMapping("/conversations/{conversationId}")
    fun setConversationWallpaper(
        @PathVariable conversationId: UUID,
        @RequestBody request: SetWallpaperRequest
    ): ResponseEntity<ApiResponse<WallpaperResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val type = request.toWallpaperType()
        val wallpaper = manageChatWallpaperUseCase.setConversationWallpaper(
            userId,
            conversationId,
            type,
            request.wallpaperValue,
            request.darkModeValue
        )
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
    wallpaperType = wallpaperType.toShared(),
    wallpaperValue = wallpaperValue,
    darkModeValue = darkModeValue,
    createdAt = createdAt.toString()
)

private fun WallpaperType.toShared(): SharedWallpaperType = when (this) {
    WallpaperType.DEFAULT -> SharedWallpaperType.DEFAULT
    WallpaperType.SOLID -> SharedWallpaperType.SOLID
    WallpaperType.GRADIENT -> SharedWallpaperType.GRADIENT
    WallpaperType.CUSTOM -> SharedWallpaperType.CUSTOM
}

/**
 * The request field is the shared [SharedWallpaperType] now (#877), which decodes a type this server
 * does not know to `UNKNOWN` instead of failing the body — so an unknown type is still answered with
 * `WALLPAPER_INVALID_TYPE`, a client error, rather than a 500 from `valueOf`. The wire value is the
 * exact upper-case name, as the shared contract has always documented it.
 */
private fun SetWallpaperRequest.toWallpaperType(): WallpaperType = when (wallpaperType) {
    SharedWallpaperType.DEFAULT -> WallpaperType.DEFAULT
    SharedWallpaperType.SOLID -> WallpaperType.SOLID
    SharedWallpaperType.GRADIENT -> WallpaperType.GRADIENT
    SharedWallpaperType.CUSTOM -> WallpaperType.CUSTOM
    SharedWallpaperType.UNKNOWN -> throw BusinessException(ErrorCode.WALLPAPER_INVALID_TYPE)
}
